package com.productstore.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.PromotionEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.PromotionRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.services.OnlinePaymentReconciliationService;
import com.productstore.platform.services.PaystackClient;
import com.productstore.platform.services.PlatformFeatureService;
import com.productstore.platform.services.SalonAccessService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End to end flows through the real HTTP API: merchant signs up, lists stock, a client registers and pays
 * online with Paystack, a signed {@code charge.success} webhook settles the payment, and both sides see it.
 * The Paystack API client is a test double that records the initialize requests; webhooks are signed
 * exactly as Paystack signs them (hex HMAC SHA512 of the raw body with the secret key).
 */
@SpringBootTest(
    classes = PlatformApplication.class,
    properties = {
      "paystack.enabled=true",
      "paystack.secret-key=" + StorePaystackE2ETest.SECRET,
      "app.frontend-base-url=http://localhost:8085",
      "paystack.verify-cache-ttl=PT0S"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorePaystackE2ETest {
  static final String SECRET = "sk_test_e2e_secret";
  private static final String PASSWORD = "Secret@123456";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ProductRepository products;
  @Autowired MerchantSubscriptionRepository subscriptions;
  @Autowired SalonServiceRepository salonServices;
  @Autowired SalonBookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired OrderRepository orders;
  @Autowired PromotionRepository promotionRepo;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired SalonStaffRepository salonStaff;
  @Autowired SalonStaffAvailabilityRepository salonAvailability;
  @Autowired OnlinePaymentReconciliationService reconciliation;
  @Autowired PlatformFeatureService platformFeatures;

  @MockitoBean PaystackClient paystack;

  private final List<PaystackClient.InitializeRequest> initialized = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    initialized.clear();
    when(paystack.initialize(any()))
        .thenAnswer(
            inv -> {
              PaystackClient.InitializeRequest r = inv.getArgument(0);
              initialized.add(r);
              return new PaystackClient.InitializeResult(
                  "https://checkout.paystack.com/" + r.reference(), r.reference());
            });
    when(paystack.verify(any())).thenReturn(Optional.empty());

    // Clients and providers can only sign up after the first run system admin exists.
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
  void merchantSignupToPaidOnlineOrderSeenByMerchantAndClient() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "50.00", 5);
    String clientEmail = uniqueEmail("client");
    String clientToken = registerClient(clientEmail);

    Map<String, Object> checkout = placeOnlineOrder(merchant.slug, clientToken, clientEmail, productId, 2);
    String orderId = (String) checkout.get("orderId");
    assertThat(checkout.get("paymentId")).isEqualTo(orderId);
    PaystackClient.InitializeRequest init = lastInitialize();
    assertThat(checkout.get("paymentRedirectUrl")).isEqualTo("https://checkout.paystack.com/" + init.reference());
    assertThat(init.amountCents()).isEqualTo(10000);
    assertThat(init.currency()).isEqualTo("ZAR");
    assertThat(init.email()).isEqualTo(clientEmail);
    assertThat(init.channels()).containsExactly("card");
    assertThat(init.reference()).matches("ORD[A-Z0-9]{16}");
    assertThat(init.callbackUrl())
        .isEqualTo("http://localhost:8085/m/" + merchant.slug + "/payment/return?kind=order&id=" + orderId);
    assertThat(init.metadata()).containsEntry("kind", "order").containsEntry("id", orderId);

    // Not paid until Paystack confirms.
    Map<String, Object> pending = orderStatus(merchant.slug, orderId, clientEmail);
    assertThat(pending.get("paid")).isEqualTo(false);
    assertThat(pending.get("cancelled")).isEqualTo(false);
    assertThat(stockOf(productId)).isEqualTo(5);

    // Forged and under paid notifications are refused and change nothing.
    byte[] good = chargeSuccess(init.reference(), 10000, "ZAR", "order", orderId);
    sendWebhook(good, "0".repeat(128)).andExpect(status().isUnauthorized());
    byte[] underpaid = chargeSuccess(init.reference(), 100, "ZAR", "order", orderId);
    sendWebhook(underpaid, sign(underpaid)).andExpect(status().isBadRequest());
    byte[] wrongCurrency = chargeSuccess(init.reference(), 10000, "USD", "order", orderId);
    sendWebhook(wrongCurrency, sign(wrongCurrency)).andExpect(status().isBadRequest());
    assertThat(orderStatus(merchant.slug, orderId, clientEmail).get("paid")).isEqualTo(false);

    // Paystack retries webhooks: settling twice must stay idempotent.
    sendWebhook(good, sign(good)).andExpect(status().isOk());
    sendWebhook(good, sign(good)).andExpect(status().isOk());

    Map<String, Object> paid = orderStatus(merchant.slug, orderId, clientEmail);
    assertThat(paid.get("paid")).isEqualTo(true);
    assertThat(paid.get("paymentMethod")).isEqualTo("online");
    assertThat(paid.get("onlinePaymentChannel")).isEqualTo("CARD");
    assertThat(stockOf(productId)).isEqualTo(3);

    Map<String, Object> adminOrder = findById(adminOrders(merchant), orderId);
    assertThat(adminOrder.get("status")).isEqualTo("paid");
    Map<String, Object> clientOrder = findById(clientOrders(clientToken), orderId);
    assertThat(clientOrder.get("status")).isEqualTo("paid");
    assertThat(clientOrder.get("canPayNow")).isEqualTo(false);
    assertThat(clientOrder.get("onlinePaymentChannel")).isEqualTo("CARD");

    // Merchant fulfils the order.
    mvc.perform(
            auth(post("/api/m/" + merchant.slug + "/admin/orders/" + orderId + "/fulfillment"), merchant.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ready\"}"))
        .andExpect(status().isOk());
    assertThat(findById(adminOrders(merchant), orderId).get("fulfillmentStatus")).isEqualTo("ready");
  }

  /** CR1: Paystack failing to start must not leave an order holding stock and a promotion use. */
  @Test
  void orderWhosePaymentCannotStartIsCancelledAndReleasesStockAndPromotion() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "40.00", 2);
    PromotionEntity promo = promotion(merchant, 1);
    String clientEmail = uniqueEmail("fail");
    String clientToken = registerClient(clientEmail);
    doThrow(new PaystackClient.PaystackException("Paystack down")).when(paystack).initialize(any());

    Map<String, Object> req = orderRequest(clientEmail, productId, 2);
    req.put("paymentMethod", "online");
    req.put("onlinePaymentChannel", "CARD");
    Map<String, Object> err =
        read(
            mvc.perform(
                    auth(post("/api/public/m/" + merchant.slug + "/checkout/orders"), clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn());
    assertThat(err.get("error")).isEqualTo("online_payment_unavailable");

    // Nothing is left pending, the promotion use is back, and the full stock can be ordered again.
    assertThat(orders.findAll())
        .filteredOn(o -> merchant.tenantId.equals(o.tenantId))
        .allMatch(o -> o.status == OrderEntity.OrderStatus.cancelled);
    assertThat(promotionRepo.findById(promo.id).orElseThrow().usageCount).isZero();
    assertThat(stockOf(productId)).isEqualTo(2);

    doAnswer(inv -> new PaystackClient.InitializeResult("https://checkout.paystack.com/ok", "ok"))
        .when(paystack)
        .initialize(any());
    placeOnlineOrder(merchant.slug, clientToken, clientEmail, productId, 2);
    assertThat(promotionRepo.findById(promo.id).orElseThrow().usageCount).isEqualTo(1);
  }

  /** CR1: a booking whose payment cannot start frees its slot. */
  @Test
  void bookingWhosePaymentCannotStartFreesTheSlot() throws Exception {
    Merchant merchant = registerMerchant();
    UUID serviceId = bookableSalon(merchant);
    String clientEmail = uniqueEmail("slot");
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("serviceId", serviceId.toString());
    req.put("startAt", nextWeekdayAt10().toString());
    req.put("customerName", "E2E Client");
    req.put("customerPhone", "0820000000");
    req.put("customerEmail", clientEmail);
    req.put("paymentMethod", "online");
    req.put("onlinePaymentChannel", "EFT");
    String url = "/api/public/m/" + merchant.slug + "/salon/bookings";

    doThrow(new PaystackClient.PaystackException("Paystack down")).when(paystack).initialize(any());
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
    assertThat(bookings.findAll())
        .filteredOn(b -> merchant.tenantId.equals(b.tenantId))
        .allMatch(b -> b.status == SalonBookingEntity.Status.cancelled);

    // The only staff member's slot is free again.
    doAnswer(inv -> new PaystackClient.InitializeResult("https://checkout.paystack.com/ok", "ok"))
        .when(paystack)
        .initialize(any());
    Map<String, Object> ok =
        read(
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(ok.get("needsOnlinePayment")).isEqualTo(true);
  }

  /** CR1 (a): nothing is saved when online payment is switched off. */
  @Test
  void onlineOrderIsRefusedBeforeSavingWhenPaystackIsUnavailable() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "10.00", 3);
    platformFeatures.setEnabled(PlatformFeatureService.PAYSTACK, false);
    try {
      Map<String, Object> req = orderRequest(uniqueEmail("off"), productId, 1);
      req.put("paymentMethod", "online");
      req.put("onlinePaymentChannel", "CARD");
      expectOrderError(merchant.slug, req, "online_payment_unavailable");
      assertThat(orders.findAll()).noneMatch(o -> merchant.tenantId.equals(o.tenantId));
      verify(paystack, never()).initialize(any());
    } finally {
      platformFeatures.setEnabled(PlatformFeatureService.PAYSTACK, true);
    }
  }

  /** CTO: no webhook ever arrives (shared Paystack account); the reconciliation job confirms the payment. */
  @Test
  void reconciliationSettlesAPaidOrderWithoutAnyWebhookAndLeavesAbandonedOnesPending() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "25.00", 5);
    String paidEmail = uniqueEmail("recon");
    String paidOrder =
        (String) placeOnlineOrder(merchant.slug, registerClient(paidEmail), paidEmail, productId, 1).get("orderId");
    String paidRef = lastInitialize().reference();
    String leftEmail = uniqueEmail("left");
    String leftOrder =
        (String) placeOnlineOrder(merchant.slug, registerClient(leftEmail), leftEmail, productId, 1).get("orderId");
    String leftRef = lastInitialize().reference();
    when(paystack.verify(paidRef))
        .thenReturn(
            Optional.of(
                new PaystackClient.Transaction(
                    "990", "success", paidRef, 2500, "ZAR", "card", "test", Map.of("kind", "order", "id", paidOrder))));
    when(paystack.verify(leftRef))
        .thenReturn(
            Optional.of(
                new PaystackClient.Transaction(
                    "991", "abandoned", leftRef, 2500, "ZAR", null, "test", Map.of("kind", "order", "id", leftOrder))));

    var summary = reconciliation.reconcile(Instant.now(), 500);

    assertThat(summary.settled()).isGreaterThanOrEqualTo(1);
    assertThat(orders.findById(UUID.fromString(paidOrder)).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.paid);
    assertThat(orders.findById(UUID.fromString(leftOrder)).orElseThrow().status)
        .isEqualTo(OrderEntity.OrderStatus.pending_payment);
    assertThat(stockOf(productId)).isEqualTo(4);

    // A second run changes nothing.
    reconciliation.reconcile(Instant.now(), 500);
    assertThat(stockOf(productId)).isEqualTo(4);
  }

  /** SEC-5: the return page can use the reference Paystack appends instead of the customer's email. */
  @Test
  void statusWorksWithTheReferenceInsteadOfTheEmail() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "15.00", 2);
    String email = uniqueEmail("ref");
    String orderId = (String) placeOnlineOrder(merchant.slug, registerClient(email), email, productId, 1).get("orderId");
    String reference = lastInitialize().reference();
    String url = "/api/public/m/" + merchant.slug + "/checkout/orders/" + orderId + "/payment-status";

    Map<String, Object> ok =
        read(mvc.perform(get(url).param("reference", reference)).andExpect(status().isOk()).andReturn());
    assertThat(ok).containsOnlyKeys("kind", "id", "status", "paymentMethod", "onlinePaymentChannel", "paid", "cancelled");

    mvc.perform(get(url).param("reference", "ORDNOTTHISONE000000")).andExpect(status().isBadRequest());
    mvc.perform(get(url)).andExpect(status().isBadRequest());
  }

  /** SEC-8: oversized bodies are refused before any signature work. */
  @Test
  void oversizedWebhookIsRefused() throws Exception {
    byte[] huge = new byte[70 * 1024];
    java.util.Arrays.fill(huge, (byte) 'a');
    sendWebhook(huge, sign(huge)).andExpect(status().isPayloadTooLarge());
  }

  @Test
  void webhookForUnknownPaymentIsAcknowledgedAndIgnored() throws Exception {
    byte[] other = chargeSuccess("SOMEOTHERAPP1", 5000, "ZAR", null, null);
    sendWebhook(other, sign(other)).andExpect(status().isOk());
  }

  /**
   * Paystack may deliver the same webhook several times at once. Every delivery must be accepted and stock
   * must drop exactly once. On the in-memory H2 test database this passes even without the row lock (H2 does
   * not reproduce the interleaving), so the MySQL row lock in {@code OrderRepository.lockByOnlinePaymentReference}
   * is what protects production.
   */
  @Test
  void concurrentWebhooksSettleTheOrderOnlyOnce() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "20.00", 10);
    String clientEmail = uniqueEmail("race");
    String clientToken = registerClient(clientEmail);
    Map<String, Object> checkout = placeOnlineOrder(merchant.slug, clientToken, clientEmail, productId, 3);
    String orderId = (String) checkout.get("orderId");
    byte[] body = chargeSuccess(lastInitialize().reference(), 6000, "ZAR", "order", orderId);
    String signature = sign(body);

    int racers = 4;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < racers; i++) {
        Callable<Integer> call =
            () -> {
              start.await();
              return sendWebhook(body, signature).andReturn().getResponse().getStatus();
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
    assertThat(orderStatus(merchant.slug, orderId, clientEmail).get("paid")).isEqualTo(true);
  }

  /** The return page confirms a payment through Paystack's verify call when the webhook is late. */
  @Test
  void statusEndpointVerifiesAPendingPaymentWhenTheWebhookIsLate() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "30.00", 4);
    String clientEmail = uniqueEmail("late");
    String clientToken = registerClient(clientEmail);
    Map<String, Object> checkout = placeOnlineOrder(merchant.slug, clientToken, clientEmail, productId, 1);
    String orderId = (String) checkout.get("orderId");
    String reference = lastInitialize().reference();
    when(paystack.verify(reference))
        .thenReturn(
            Optional.of(
                new PaystackClient.Transaction(
                    "777", "success", reference, 3000, "ZAR", "card", "test", Map.of("kind", "order", "id", orderId))));

    Map<String, Object> status = orderStatus(merchant.slug, orderId, clientEmail);

    assertThat(status.get("paid")).isEqualTo(true);
    assertThat(stockOf(productId)).isEqualTo(3);
  }

  @Test
  void abandonedCheckoutIsReportedAsCancelled() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "30.00", 4);
    String clientEmail = uniqueEmail("abandon");
    String clientToken = registerClient(clientEmail);
    String orderId = (String) placeOnlineOrder(merchant.slug, clientToken, clientEmail, productId, 1).get("orderId");
    String reference = lastInitialize().reference();
    when(paystack.verify(reference))
        .thenReturn(Optional.of(new PaystackClient.Transaction("778", "abandoned", reference, 3000, "ZAR", null, "test", Map.of("kind", "order", "id", orderId))));

    Map<String, Object> status = orderStatus(merchant.slug, orderId, clientEmail);

    assertThat(status.get("paid")).isEqualTo(false);
    assertThat(status.get("cancelled")).isEqualTo(true);
  }

  @Test
  void clientPaysPendingBookingOnline() throws Exception {
    Merchant merchant = registerMerchant();
    String clientEmail = uniqueEmail("booker");
    String clientToken = registerClient(clientEmail);
    SalonBookingEntity booking = seedPendingOnlineBooking(merchant, clientEmail, new BigDecimal("180.00"));

    Map<String, Object> started =
        read(
            mvc.perform(auth(post("/api/clients/me/bookings/" + booking.id + "/online-checkout"), clientToken))
                .andExpect(status().isOk())
                .andReturn());
    PaystackClient.InitializeRequest init = lastInitialize();
    assertThat(started.get("paymentId")).isEqualTo(booking.id.toString());
    assertThat(started.get("paymentRedirectUrl")).isEqualTo("https://checkout.paystack.com/" + init.reference());
    assertThat(init.amountCents()).isEqualTo(18000);
    assertThat(init.channels()).containsExactly("eft");
    assertThat(init.reference()).matches("BKG[A-Z0-9]{16}");
    assertThat(init.callbackUrl()).contains("/payment/return?kind=booking&id=" + booking.id);

    // Another client cannot start a payment for this booking.
    String otherToken = registerClient(uniqueEmail("other"));
    mvc.perform(auth(post("/api/clients/me/bookings/" + booking.id + "/online-checkout"), otherToken))
        .andExpect(status().isNotFound());

    byte[] body = chargeSuccess(init.reference(), 18000, "ZAR", "booking", booking.id.toString());
    sendWebhook(body, sign(body)).andExpect(status().isOk());
    sendWebhook(body, sign(body)).andExpect(status().isOk());

    Map<String, Object> status =
        read(
            mvc.perform(
                    get("/api/public/m/" + merchant.slug + "/salon/bookings/" + booking.id + "/payment-status")
                        .param("customerEmail", clientEmail))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(status.get("paid")).isEqualTo(true);
    assertThat(status.get("status")).isEqualTo("confirmed");
  }

  @Test
  void merchantPaysSubscriptionOnline() throws Exception {
    Merchant merchant = registerMerchant();
    expireTrial(merchant);

    Map<String, Object> before = subscriptionMe(merchant);
    assertThat(before.get("onlinePaymentConfigured")).isEqualTo(true);

    MvcResult started =
        mvc.perform(
                auth(post("/api/m/" + merchant.slug + "/admin/subscription/online-checkout"), merchant.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"onlinePaymentChannel\":\"CARD\"}"))
            .andExpect(status().isOk())
            .andReturn();
    Map<String, Object> session = read(started);
    PaystackClient.InitializeRequest init = lastInitialize();
    assertThat(session.get("paymentRedirectUrl")).isEqualTo("https://checkout.paystack.com/" + init.reference());
    assertThat(init.email()).isEqualTo(merchant.ownerEmail);
    assertThat(init.reference()).matches("SUB[A-Z0-9]{16}");
    assertThat(init.callbackUrl())
        .isEqualTo(
            "http://localhost:8085/payment/return?kind=subscription&paymentId=" + session.get("paymentId")
                + "&merchant=" + merchant.slug);

    byte[] body =
        chargeSuccess(init.reference(), init.amountCents(), "ZAR", "subscription", (String) session.get("paymentId"));
    sendWebhook(body, sign(body)).andExpect(status().isOk());
    sendWebhook(body, sign(body)).andExpect(status().isOk());

    Map<String, Object> me = subscriptionMe(merchant);
    assertThat(me.get("active")).isEqualTo(true);
    assertThat(me.get("valid")).isEqualTo(true);
    assertThat(me.get("onTrial")).isEqualTo(false);
    assertThat(me.get("onlinePaymentChannel")).isEqualTo("CARD");
  }

  /** The subscription return page confirms the payment through Paystack's verify call when the webhook is late. */
  @Test
  void subscriptionStatusSettlesAPendingPaymentAndIsTenantScoped() throws Exception {
    Merchant merchant = registerMerchant();
    expireTrial(merchant);
    Map<String, Object> session =
        read(
            mvc.perform(
                    auth(post("/api/m/" + merchant.slug + "/admin/subscription/online-checkout"), merchant.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlinePaymentChannel\":\"EFT\"}"))
                .andExpect(status().isOk())
                .andReturn());
    String paymentId = (String) session.get("paymentId");
    PaystackClient.InitializeRequest init = lastInitialize();
    String statusUrl = "/api/m/" + merchant.slug + "/admin/subscription/online-payments/" + paymentId + "/status";

    Map<String, Object> pending = read(mvc.perform(auth(get(statusUrl), merchant.token)).andExpect(status().isOk()).andReturn());
    assertThat(pending.get("paid")).isEqualTo(false);
    assertThat(pending.get("cancelled")).isEqualTo(false);
    assertThat(pending.get("status")).isEqualTo("pending_online");

    when(paystack.verify(init.reference()))
        .thenReturn(
            Optional.of(
                new PaystackClient.Transaction(
                    "880", "success", init.reference(), init.amountCents(), "ZAR", "eft", "test",
                    Map.of("kind", "subscription", "id", paymentId))));
    Map<String, Object> paid = read(mvc.perform(auth(get(statusUrl), merchant.token)).andExpect(status().isOk()).andReturn());
    assertThat(paid).containsEntry("paymentId", paymentId).containsEntry("paid", true).containsEntry("status", "completed");
    // Asking again (and a late webhook) changes nothing.
    assertThat(read(mvc.perform(auth(get(statusUrl), merchant.token)).andExpect(status().isOk()).andReturn()).get("paid"))
        .isEqualTo(true);
    byte[] late = chargeSuccess(init.reference(), init.amountCents(), "ZAR", "subscription", paymentId);
    sendWebhook(late, sign(late)).andExpect(status().isOk());
    Map<String, Object> me = subscriptionMe(merchant);
    assertThat(me.get("active")).isEqualTo(true);
    assertThat(me.get("onlinePaymentChannel")).isEqualTo("EFT");

    // Another merchant cannot read this payment through its own shop.
    Merchant other = registerMerchant();
    mvc.perform(
            auth(
                get("/api/m/" + other.slug + "/admin/subscription/online-payments/" + paymentId + "/status"),
                other.token))
        .andExpect(status().isNotFound());
  }

  @Test
  void subscriptionStatusReportsAnAbandonedCheckoutAsCancelled() throws Exception {
    Merchant merchant = registerMerchant();
    expireTrial(merchant);
    String paymentId =
        (String)
            read(
                    mvc.perform(
                            auth(post("/api/m/" + merchant.slug + "/admin/subscription/online-checkout"), merchant.token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"onlinePaymentChannel\":\"CARD\"}"))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("paymentId");
    String reference = lastInitialize().reference();
    when(paystack.verify(reference))
        .thenReturn(Optional.of(new PaystackClient.Transaction("881", "abandoned", reference, 100, "ZAR", null, "test", Map.of("kind", "subscription", "id", paymentId))));

    Map<String, Object> status =
        read(
            mvc.perform(
                    auth(
                        get("/api/m/" + merchant.slug + "/admin/subscription/online-payments/" + paymentId + "/status"),
                        merchant.token))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(status.get("paid")).isEqualTo(false);
    assertThat(status.get("cancelled")).isEqualTo(true);
  }

  @Test
  void onlineOrderRequiresAValidChannel() throws Exception {
    Merchant merchant = registerMerchant();
    UUID productId = createProduct(merchant, "10.00", 2);
    String email = uniqueEmail("chan");
    Map<String, Object> req = orderRequest(email, productId, 1);
    req.put("paymentMethod", "online");
    expectOrderError(merchant.slug, req, "online_payment_channel_required");
    req.put("onlinePaymentChannel", "PAYBYBANK");
    expectOrderError(merchant.slug, req, "invalid_online_payment_channel");
    req.put("paymentMethod", "cash_store");
    req.put("onlinePaymentChannel", "CARD");
    expectOrderError(merchant.slug, req, "online_payment_channel_not_applicable");
  }

  @Test
  void publicSettingsAndConfiguredFlagShowOnlinePayment() throws Exception {
    Merchant merchant = registerMerchant();
    Map<String, Object> settings =
        read(mvc.perform(get("/api/public/m/" + merchant.slug + "/shop-settings")).andExpect(status().isOk()).andReturn());
    assertThat(settings.get("acceptCustomerOnline")).isEqualTo(true);
    assertThat(settings.get("onlinePaymentConfigured")).isEqualTo(true);
    assertThat(settings).containsKeys("acceptCustomerEft", "acceptCustomerCash", "customerPaymentsCashOnly");

    Map<String, Object> configured =
        read(mvc.perform(get("/api/payments/paystack/configured")).andExpect(status().isOk()).andReturn());
    assertThat(configured.get("configured")).isEqualTo(true);
  }

  // ---------------------------------------------------------------- API steps

  private record Merchant(String slug, String token, UUID tenantId, String ownerEmail) {}

  private Merchant registerMerchant() throws Exception {
    String ownerEmail = uniqueEmail("owner");
    String body =
        json.writeValueAsString(
            Map.of(
                "merchantName", "E2E Shop " + UUID.randomUUID().toString().substring(0, 8),
                "ownerEmail", ownerEmail,
                "ownerPassword", PASSWORD));
    Map<String, Object> out =
        read(mvc.perform(post("/api/auth/register-merchant").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn());
    @SuppressWarnings("unchecked")
    Map<String, Object> tenant = (Map<String, Object>) out.get("tenant");
    return new Merchant(
        (String) out.get("merchantSlug"),
        (String) out.get("token"),
        UUID.fromString((String) tenant.get("id")),
        ownerEmail);
  }

  /** Day 8: the one time 7 day trial is over, so the merchant must pay to continue. */
  private void expireTrial(Merchant merchant) {
    var sub = subscriptions.findByTenantId(merchant.tenantId).orElseThrow();
    sub.trialStartAt = Instant.now().minus(Duration.ofDays(8));
    sub.trialEndAt = Instant.now().minus(Duration.ofDays(1));
    subscriptions.save(sub);
  }

  private Map<String, Object> subscriptionMe(Merchant merchant) throws Exception {
    return read(
        mvc.perform(auth(get("/api/m/" + merchant.slug + "/admin/subscription/me"), merchant.token))
            .andExpect(status().isOk())
            .andReturn());
  }

  /** A 10% promotion the checkout applies automatically, limited to {@code usageLimit} uses. */
  private PromotionEntity promotion(Merchant merchant, int usageLimit) {
    PromotionEntity p = new PromotionEntity();
    p.id = UUID.randomUUID();
    p.tenantId = merchant.tenantId;
    p.title = "Ten off";
    p.description = "Ten percent off";
    p.discountType = "PERCENTAGE";
    p.discountValue = 10.0;
    p.startDate = java.time.LocalDate.now().minusDays(1);
    p.endDate = java.time.LocalDate.now().plusDays(30);
    p.active = true;
    p.usageLimit = usageLimit;
    p.usageCount = 0;
    p.createdAt = Instant.now();
    return promotionRepo.save(p);
  }

  /** Makes the merchant a salon with one service and one staff member free every day 08:00 to 18:00. */
  private UUID bookableSalon(Merchant merchant) {
    var settings =
        shopSettings
            .findByTenantId(merchant.tenantId)
            .orElseGet(() -> com.productstore.platform.services.ShopSettingsDefaults.newRowForTenant(merchant.tenantId));
    settings.shopType = SalonAccessService.SHOP_SALON_AND_STORE;
    settings.openingHoursJson = null;
    shopSettings.save(settings);

    SalonStaffEntity staffMember = new SalonStaffEntity();
    staffMember.id = UUID.randomUUID();
    staffMember.tenantId = merchant.tenantId;
    staffMember.displayName = "Stylist";
    staffMember.active = true;
    staffMember.createdAt = Instant.now();
    salonStaff.save(staffMember);
    for (int day = 1; day <= 7; day++) {
      SalonStaffAvailabilityEntity w = new SalonStaffAvailabilityEntity();
      w.id = UUID.randomUUID();
      w.tenantId = merchant.tenantId;
      w.staffId = staffMember.id;
      w.dayOfWeek = day;
      w.startTime = java.time.LocalTime.of(8, 0);
      w.endTime = java.time.LocalTime.of(18, 0);
      w.createdAt = Instant.now();
      salonAvailability.save(w);
    }

    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = UUID.randomUUID();
    svc.tenantId = merchant.tenantId;
    svc.name = "Cut";
    svc.description = "Hair cut";
    svc.durationMinutes = 30;
    svc.priceZar = new BigDecimal("100.00");
    svc.active = true;
    svc.createdAt = Instant.now();
    salonServices.save(svc);
    return svc.id;
  }

  private static Instant nextWeekdayAt10() {
    java.time.ZoneId zone = java.time.ZoneId.of("Africa/Johannesburg");
    return java.time.LocalDate.now(zone).plusDays(7).atTime(10, 0).atZone(zone).toInstant();
  }

  private SalonBookingEntity seedPendingOnlineBooking(Merchant merchant, String clientEmail, BigDecimal amount) {
    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = UUID.randomUUID();
    svc.tenantId = merchant.tenantId;
    svc.name = "Cut";
    svc.description = "Hair cut";
    svc.durationMinutes = 30;
    svc.priceZar = amount;
    svc.active = true;
    svc.createdAt = Instant.now();
    salonServices.save(svc);

    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = merchant.tenantId;
    b.serviceId = svc.id;
    b.customerName = "E2E Client";
    b.customerPhone = "0820000000";
    b.customerEmail = clientEmail;
    b.startAt = Instant.now().plus(Duration.ofDays(2));
    b.endAt = b.startAt.plus(Duration.ofMinutes(30));
    b.status = SalonBookingEntity.Status.pending;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    b.onlinePaymentChannel = OnlinePaymentChannel.EFT;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    b.amountZar = amount;
    b.clientUserId = users.findByEmailIgnoreCase(clientEmail).orElseThrow().id;
    b.createdAt = Instant.now();
    return bookings.save(b);
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

  private Map<String, Object> orderRequest(String clientEmail, UUID productId, int qty) {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("customerName", "E2E Client");
    req.put("customerEmail", clientEmail);
    req.put("customerPhone", "0820000000");
    req.put("deliveryType", "pickup");
    req.put("items", List.of(Map.of("product_id", productId.toString(), "quantity", qty)));
    return req;
  }

  private Map<String, Object> placeOnlineOrder(
      String slug, String clientToken, String clientEmail, UUID productId, int qty) throws Exception {
    Map<String, Object> req = orderRequest(clientEmail, productId, qty);
    req.put("paymentMethod", "online");
    req.put("onlinePaymentChannel", "CARD");
    Map<String, Object> out =
        read(mvc.perform(
                auth(post("/api/public/m/" + slug + "/checkout/orders"), clientToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn());
    assertThat(out.get("needsOnlinePayment")).isEqualTo(true);
    return out;
  }

  private void expectOrderError(String slug, Map<String, Object> req, String code) throws Exception {
    Map<String, Object> out =
        read(mvc.perform(
                post("/api/public/m/" + slug + "/checkout/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andReturn());
    assertThat(out.get("error")).isEqualTo(code);
  }

  private Map<String, Object> orderStatus(String slug, String orderId, String email) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + slug + "/checkout/orders/" + orderId + "/payment-status")
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

  private PaystackClient.InitializeRequest lastInitialize() {
    assertThat(initialized).isNotEmpty();
    return initialized.get(initialized.size() - 1);
  }

  /** A {@code charge.success} body as Paystack sends it. */
  private byte[] chargeSuccess(String reference, long amountCents, String currency, String kind, String id)
      throws Exception {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (kind != null) metadata.put("kind", kind);
    if (id != null) metadata.put("id", id);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 4099260516L);
    data.put("domain", "test");
    data.put("status", "success");
    data.put("reference", reference);
    data.put("amount", amountCents);
    data.put("currency", currency);
    data.put("channel", "card");
    data.put("metadata", metadata);
    return json.writeValueAsBytes(Map.of("event", "charge.success", "data", data));
  }

  private ResultActions sendWebhook(byte[] body, String signature) throws Exception {
    return mvc.perform(
        post("/api/payments/paystack/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-paystack-signature", signature)
            .content(body));
  }

  private static String sign(byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }

  // ---------------------------------------------------------------- helpers

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
