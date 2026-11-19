package com.productstore.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.util.PayFastSignatureUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end flows through the real HTTP API (Wheel Hub E2E standard): merchant signs up,
 * lists stock, a client registers and pays with PayFast, PayFast's ITN settles the order, and
 * both sides see it. The ITN is simulated with a correctly signed form post, exactly as PayFast
 * sends it to {@code /api/payments/payfast/webhook}.
 */
@SpringBootTest(
    classes = PlatformApplication.class,
    properties = {
      "payfast.enabled=true",
      "payfast.sandbox=true",
      "payfast.merchant-id=10000100",
      "payfast.merchant-key=46f0cd694581a",
      "payfast.passphrase=e2e-passphrase",
      "payfast.public-base-url=https://api.e2e.test",
      "payfast.itn-validate=false",
      "app.frontend-base-url=http://localhost:8085"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorePayFastE2ETest {
  private static final String PASSPHRASE = "e2e-passphrase";
  private static final String PASSWORD = "Secret@123456";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ProductRepository products;
  @Autowired MerchantSubscriptionRepository subscriptions;

  /** Clients and providers can only sign up after the first-run system admin exists. */
  @BeforeEach
  void ensureSystemAdmin() throws Exception {
    String body = json.writeValueAsString(Map.of("email", uniqueEmail("sysadmin"), "password", PASSWORD));
    int code =
        mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(code).isIn(201, 409);
  }

  // ---------------------------------------------------------------- flows

  @Test
  void merchantSignupToPaidPayFastOrderSeenByMerchantAndClient() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "50.00", 5);
    String clientEmail = uniqueEmail("client");
    String clientToken = registerClient(clientEmail);

    Map<String, Object> checkout = placePayFastOrder(merchant.slug, clientToken, clientEmail, productId, 2);
    String orderId = (String) checkout.get("orderId");
    @SuppressWarnings("unchecked")
    Map<String, String> fields = (Map<String, String>) checkout.get("fields");
    assertThat(checkout.get("processUrl")).isEqualTo("https://sandbox.payfast.co.za/eng/process");
    assertThat(fields.get("amount")).isEqualTo("100.00");
    assertThat(fields.get("return_url")).startsWith("http://localhost:8085");
    assertThat(fields.get("notify_url")).isEqualTo("https://api.e2e.test/api/payments/payfast/webhook");

    // Not paid until PayFast confirms.
    assertThat(orderStatus(merchant.slug, orderId, clientEmail).get("paid")).isEqualTo(false);
    assertThat(stockOf(productId)).isEqualTo(5);

    // Tampered and under-paid notifications are refused and change nothing.
    Map<String, String> tampered = itn(fields, "100.00");
    tampered.put("signature", "0".repeat(32));
    sendItn(tampered).andExpect(status().isBadRequest()).andExpect(content().string("INVALID"));
    sendItn(itn(fields, "1.00")).andExpect(status().isBadRequest());
    assertThat(orderStatus(merchant.slug, orderId, clientEmail).get("paid")).isEqualTo(false);

    // PayFast retries ITNs: settling twice must stay idempotent.
    sendItn(itn(fields, "100.00")).andExpect(status().isOk()).andExpect(content().string("OK"));
    sendItn(itn(fields, "100.00")).andExpect(status().isOk());

    assertThat(orderStatus(merchant.slug, orderId, clientEmail).get("paid")).isEqualTo(true);
    assertThat(stockOf(productId)).isEqualTo(3);

    Map<String, Object> adminOrder = findById(adminOrders(merchant), orderId);
    assertThat(adminOrder.get("status")).isEqualTo("paid");
    Map<String, Object> clientOrder = findById(clientOrders(clientToken), orderId);
    assertThat(clientOrder.get("status")).isEqualTo("paid");
    assertThat(clientOrder.get("canPayNow")).isEqualTo(false);

    // Merchant fulfils the order.
    mvc.perform(
            auth(post("/api/m/" + merchant.slug + "/admin/orders/" + orderId + "/fulfillment"), merchant.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ready\"}"))
        .andExpect(status().isOk());
    assertThat(findById(adminOrders(merchant), orderId).get("fulfillmentStatus")).isEqualTo("ready");
  }

  /**
   * PayFast may deliver the same ITN several times at once. Every delivery must be accepted and
   * stock must drop exactly once. Note: on the in-memory H2 test database this passes even
   * without the row lock (H2 does not reproduce the interleaving), so the MySQL row lock in
   * {@code OrderRepository.lockByPeachMerchantTransactionId} is what protects production.
   */
  @Test
  void concurrentPayFastNotificationsSettleTheOrderOnlyOnce() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "20.00", 10);
    String clientEmail = uniqueEmail("race");
    String clientToken = registerClient(clientEmail);
    Map<String, Object> checkout = placePayFastOrder(merchant.slug, clientToken, clientEmail, productId, 3);
    @SuppressWarnings("unchecked")
    Map<String, String> fields = (Map<String, String>) checkout.get("fields");

    int racers = 4;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < racers; i++) {
        Callable<Integer> call =
            () -> {
              start.await();
              return sendItn(itn(fields, "60.00")).andReturn().getResponse().getStatus();
            };
        results.add(pool.submit(call));
      }
      start.countDown();
      for (Future<Integer> f : results) {
        assertThat(f.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(stockOf(productId)).isEqualTo(7);
    assertThat(orderStatus(merchant.slug, (String) checkout.get("orderId"), clientEmail).get("paid"))
        .isEqualTo(true);
  }

  @Test
  void merchantPaysSubscriptionWithPayFast() throws Exception {
    Merchant merchant = registerMerchant();
    expireTrial(merchant);
    MvcResult started =
        mvc.perform(
                auth(post("/api/m/" + merchant.slug + "/admin/subscription/payfast-checkout"), merchant.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"payFastPaymentMethod\":\"CARD\"}"))
            .andExpect(status().isOk())
            .andReturn();
    Map<String, Object> session = read(started);
    @SuppressWarnings("unchecked")
    Map<String, String> fields = (Map<String, String>) session.get("fields");
    assertThat(fields.get("notify_url")).endsWith("/api/payments/payfast/subscription/webhook");

    Map<String, String> itn = itn(fields, fields.get("amount"));
    mvc.perform(form(post("/api/payments/payfast/subscription/webhook"), itn)).andExpect(status().isOk());
    mvc.perform(form(post("/api/payments/payfast/subscription/webhook"), itn)).andExpect(status().isOk());

    Map<String, Object> me =
        read(mvc.perform(auth(get("/api/m/" + merchant.slug + "/admin/subscription/me"), merchant.token))
            .andExpect(status().isOk())
            .andReturn());
    assertThat(me.get("active")).isEqualTo(true);
    assertThat(me.get("valid")).isEqualTo(true);
    assertThat(me.get("onTrial")).isEqualTo(false);
  }

  // ---------------------------------------------------------------- API steps

  private record Merchant(String slug, String token, UUID tenantId) {}

  private Merchant registerMerchant() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "merchantName", "E2E Shop " + UUID.randomUUID().toString().substring(0, 8),
                "ownerEmail", uniqueEmail("owner"),
                "ownerPassword", PASSWORD));
    Map<String, Object> out =
        read(mvc.perform(post("/api/auth/register-merchant").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn());
    @SuppressWarnings("unchecked")
    Map<String, Object> tenant = (Map<String, Object>) out.get("tenant");
    return new Merchant(
        (String) out.get("merchantSlug"), (String) out.get("token"), UUID.fromString((String) tenant.get("id")));
  }

  /** Day 8: the one-time 7-day trial is over, so the merchant must pay to continue. */
  private void expireTrial(Merchant merchant) {
    var sub = subscriptions.findByTenantId(merchant.tenantId).orElseThrow();
    sub.trialStartAt = Instant.now().minus(Duration.ofDays(8));
    sub.trialEndAt = Instant.now().minus(Duration.ofDays(1));
    subscriptions.save(sub);
  }

  private UUID createProduct(Merchant merchant, String price, int stock) throws Exception {
    String body =
        json.writeValueAsString(
            Map.of("name", "E2E Widget", "category", "Goods", "price", price, "stock", String.valueOf(stock)));
    Map<String, Object> out =
        read(mvc.perform(
                auth(post("/api/m/" + merchant.slug + "/admin/products"), merchant.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is2xxSuccessful())
            .andReturn());
    return UUID.fromString((String) out.get("id"));
  }

  private String registerClient(String email) throws Exception {
    String body =
        json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "E2E Client"));
    Map<String, Object> out =
        read(mvc.perform(post("/api/auth/register-client").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn());
    return (String) out.get("token");
  }

  private Map<String, Object> placePayFastOrder(
      String slug, String clientToken, String clientEmail, UUID productId, int qty) throws Exception {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("customerName", "E2E Client");
    req.put("customerEmail", clientEmail);
    req.put("customerPhone", "0820000000");
    req.put("deliveryType", "pickup");
    req.put("paymentMethod", "payfast");
    req.put("payFastPaymentMethod", "CARD");
    req.put("items", List.of(Map.of("product_id", productId.toString(), "quantity", qty)));
    Map<String, Object> out =
        read(mvc.perform(
                auth(post("/api/public/m/" + slug + "/checkout/orders"), clientToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn());
    assertThat(out.get("needsPayFastCheckout")).isEqualTo(true);
    return out;
  }

  private Map<String, Object> orderStatus(String slug, String orderId, String email) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + slug + "/checkout/orders/" + orderId + "/payfast-status")
                .param("customerEmail", email))
            .andExpect(status().isOk())
            .andReturn());
  }

  private List<Map<String, Object>> adminOrders(Merchant merchant) throws Exception {
    Object out =
        readAny(mvc.perform(auth(get("/api/m/" + merchant.slug + "/admin/orders"), merchant.token))
            .andExpect(status().isOk())
            .andReturn());
    return listIn(out);
  }

  private List<Map<String, Object>> clientOrders(String clientToken) throws Exception {
    Object out =
        readAny(mvc.perform(auth(get("/api/clients/me/orders"), clientToken)).andExpect(status().isOk()).andReturn());
    return listIn(out);
  }

  /** Builds the ITN PayFast posts back, signed with the merchant passphrase. */
  private static Map<String, String> itn(Map<String, String> checkoutFields, String amountGross) {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("m_payment_id", checkoutFields.get("m_payment_id"));
    p.put("pf_payment_id", "PF" + UUID.randomUUID().toString().substring(0, 8));
    p.put("payment_status", "COMPLETE");
    p.put("item_name", checkoutFields.get("item_name"));
    p.put("amount_gross", amountGross);
    p.put("custom_str1", checkoutFields.get("custom_str1"));
    p.put("custom_str2", checkoutFields.get("custom_str2"));
    p.put("merchant_id", checkoutFields.get("merchant_id"));
    p.put("signature", PayFastSignatureUtil.buildSignature(p, PASSPHRASE));
    return p;
  }

  private org.springframework.test.web.servlet.ResultActions sendItn(Map<String, String> params) throws Exception {
    return mvc.perform(form(post("/api/payments/payfast/webhook"), params));
  }

  // ---------------------------------------------------------------- helpers

  private static MockHttpServletRequestBuilder form(MockHttpServletRequestBuilder b, Map<String, String> params) {
    b.contentType(MediaType.APPLICATION_FORM_URLENCODED);
    params.forEach((k, v) -> b.param(k, v == null ? "" : v));
    return b;
  }

  private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
    return b.header("Authorization", "Bearer " + token);
  }

  private int stockOf(UUID productId) {
    return products.findById(productId).orElseThrow().stock;
  }

  private Map<String, Object> read(MvcResult r) throws Exception {
    return json.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {});
  }

  private Object readAny(MvcResult r) throws Exception {
    return json.readValue(r.getResponse().getContentAsString(), Object.class);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listIn(Object raw) {
    if (raw instanceof List<?> top) {
      return (List<Map<String, Object>>) top;
    }
    Map<String, Object> out = (Map<String, Object>) raw;
    for (Object v : out.values()) {
      if (v instanceof List<?> l && (l.isEmpty() || l.get(0) instanceof Map)) {
        return (List<Map<String, Object>>) l;
      }
    }
    throw new AssertionError("No list of records in response: " + out.keySet());
  }

  private static Map<String, Object> findById(List<Map<String, Object>> rows, String id) {
    return rows.stream()
        .filter(r -> id.equals(String.valueOf(r.get("id"))))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Order " + id + " not listed"));
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@e2e.test";
  }

}
