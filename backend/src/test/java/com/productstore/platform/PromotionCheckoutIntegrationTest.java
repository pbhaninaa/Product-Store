package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.PromotionEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.PromotionRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.services.MerchantSubscriptionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Promotions are applied (and validated) by the server when a shopper checks out. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromotionCheckoutIntegrationTest {

  private static final String SLUG = "promo-shop";
  private static final String OTHER_SLUG = "promo-other-shop";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TenantRepository tenantRepository;
  @Autowired ShopSettingsRepository shopSettingsRepository;
  @Autowired ProductRepository productRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderItemRepository orderItemRepository;
  @Autowired PromotionRepository promotionRepository;
  @Autowired MerchantSubscriptionService subscriptions;

  private UUID tenantId;
  private UUID otherTenantId;
  private UUID productId;

  @BeforeEach
  void seed() {
    cleanup();
    tenantId = tenant(SLUG);
    otherTenantId = tenant(OTHER_SLUG);

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.name = "Promo Widget";
    p.category = "Goods";
    p.priceZar = new BigDecimal("100.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 20;
    p.createdAt = Instant.now();
    productRepository.save(p);
    productId = p.id;
  }

  @AfterEach
  void cleanup() {
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    promotionRepository.deleteAll();
    productRepository.deleteAll();
    shopSettingsRepository.deleteAll();
    tenantRepository.findBySlug(SLUG).ifPresent(tenantRepository::delete);
    tenantRepository.findBySlug(OTHER_SLUG).ifPresent(tenantRepository::delete);
  }

  @Test
  void validPromotionIdReducesTheOrderTotal() throws Exception {
    PromotionEntity promo = promo(tenantId, "PERCENTAGE", 10d, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

    JsonNode created = placeOrder(promo.id.toString(), 201);
    OrderEntity o = orderRepository.findById(UUID.fromString(created.get("orderId").asText())).orElseThrow();
    assertThat(o.subtotalZar).isEqualByComparingTo("200.00");
    assertThat(o.totalZar).isEqualByComparingTo("180.00");
    assertThat(promotionRepository.findById(promo.id).orElseThrow().usageCount).isEqualTo(1);
  }

  @Test
  void bestValidPromotionIsAppliedAutomaticallyAndQuoted() throws Exception {
    promo(tenantId, "FIXED", 15d, LocalDate.now(), LocalDate.now());
    PromotionEntity best = promo(tenantId, "PERCENTAGE", 25d, LocalDate.now(), LocalDate.now());
    // Expired and other-store offers must never be picked.
    promo(tenantId, "PERCENTAGE", 90d, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
    promo(otherTenantId, "PERCENTAGE", 80d, LocalDate.now(), LocalDate.now());

    mvc.perform(
            post("/api/public/m/" + SLUG + "/promotions/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"product_id\":\"" + productId + "\",\"quantity\":2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.promoId").value(best.id.toString()))
        .andExpect(jsonPath("$.discountZar").value("50.00"));

    JsonNode created = placeOrder(null, 201);
    OrderEntity o = orderRepository.findById(UUID.fromString(created.get("orderId").asText())).orElseThrow();
    assertThat(o.totalZar).isEqualByComparingTo("150.00");
  }

  @Test
  void expiredPromotionIsRejected() throws Exception {
    PromotionEntity expired =
        promo(tenantId, "PERCENTAGE", 10d, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
    JsonNode err = placeOrder(expired.id.toString(), 400);
    assertThat(err.get("error").asText()).isEqualTo("promotion_not_active");
    assertThat(orderRepository.count()).isZero();
  }

  @Test
  void promotionFromAnotherStoreIsRejected() throws Exception {
    PromotionEntity foreign = promo(otherTenantId, "PERCENTAGE", 50d, LocalDate.now(), LocalDate.now());
    JsonNode err = placeOrder(foreign.id.toString(), 400);
    assertThat(err.get("error").asText()).isEqualTo("promotion_not_found");
    assertThat(orderRepository.count()).isZero();
  }

  @Test
  void categoryRestrictedPromotionOnlyAppliesToMatchingProducts() throws Exception {
    PromotionEntity promo = promo(tenantId, "PERCENTAGE", 10d, LocalDate.now(), LocalDate.now());
    promo.applicableCategories = "Shoes, Hats";
    promotionRepository.save(promo);
    JsonNode err = placeOrder(promo.id.toString(), 400);
    assertThat(err.get("error").asText()).isEqualTo("promotion_not_applicable");
  }

  private JsonNode placeOrder(String promoId, int expectedStatus) throws Exception {
    String promoJson = promoId == null ? "" : ",\"promoId\":\"" + promoId + "\"";
    String body =
        "{\"customerName\":\"Pat Shopper\",\"customerEmail\":\"pat@shopper.test\","
            + "\"customerPhone\":\"0820000000\",\"deliveryType\":\"pickup\","
            + "\"paymentMethod\":\"cash_store\""
            + promoJson
            + ",\"items\":[{\"product_id\":\""
            + productId
            + "\",\"quantity\":2}]}";
    String res =
        mvc.perform(
                post("/api/public/m/" + SLUG + "/checkout/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res);
  }

  private PromotionEntity promo(UUID tenant, String type, double value, LocalDate start, LocalDate end) {
    PromotionEntity p = new PromotionEntity();
    p.tenantId = tenant;
    p.title = type + " " + value;
    p.discountType = type;
    p.discountValue = value;
    p.startDate = start;
    p.endDate = end;
    p.active = true;
    return promotionRepository.save(p);
  }

  private UUID tenant(String slug) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = slug;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);

    Instant now = Instant.now();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = t.id;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = "";
    s.bankAccountHolder = "";
    s.bankAccountNumber = "";
    s.bankBranchCode = "";
    s.storeName = slug;
    s.contactEmail = "shop@b.co";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "normal_store";
    s.openingHoursJson = "[]";
    s.acceptCustomerPeach = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    shopSettingsRepository.save(s);
    subscriptions.forceActivatePlan(t.id, TenantEntity.SubscriptionPlan.STANDARD);
    return t.id;
  }
}
