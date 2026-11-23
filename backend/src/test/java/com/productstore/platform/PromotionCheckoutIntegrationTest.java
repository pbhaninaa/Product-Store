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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.PromotionService;
import com.productstore.platform.services.MerchantSubscriptionService;
import java.time.Duration;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired CheckoutService checkoutService;
  @Autowired PromotionService promotionService;
  @Autowired TransactionTemplate tx;

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

  @Test
  void linkedPromotionOnlyDiscountsTheProductsItNames() throws Exception {
    ProductEntity other = new ProductEntity();
    other.id = UUID.randomUUID();
    other.tenantId = tenantId;
    other.name = "Not on promo";
    other.category = "Goods";
    other.priceZar = new BigDecimal("50.00");
    other.imageUrl = "";
    other.imagePath = "";
    other.stock = 20;
    other.createdAt = Instant.now();
    productRepository.save(other);

    PromotionEntity promo = promo(tenantId, "PERCENTAGE", 10d, LocalDate.now(), LocalDate.now());
    promo.applicableItemIds = productId.toString();
    promotionRepository.save(promo);

    String body =
        "{\"customerName\":\"Pat Shopper\",\"customerEmail\":\"pat@shopper.test\","
            + "\"customerPhone\":\"0820000000\",\"deliveryType\":\"pickup\",\"paymentMethod\":\"cash_store\","
            + "\"promoId\":\"" + promo.id + "\",\"items\":["
            + "{\"product_id\":\"" + productId + "\",\"quantity\":2},"
            + "{\"product_id\":\"" + other.id + "\",\"quantity\":2}]}";
    String res =
        mvc.perform(
                post("/api/public/m/" + SLUG + "/checkout/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is(201))
            .andReturn()
            .getResponse()
            .getContentAsString();
    OrderEntity o = orderRepository.findById(UUID.fromString(json.readTree(res).get("orderId").asText())).orElseThrow();
    // 10% of the linked 2 x R100 only, not of the R100 for the other product.
    assertThat(o.subtotalZar).isEqualByComparingTo("300.00");
    assertThat(o.totalZar).isEqualByComparingTo("280.00");

    // A cart with only the unlinked product gets nothing from it.
    promotionRepository.save(promo);
    String onlyOther =
        body.replace("{\"product_id\":\"" + productId + "\",\"quantity\":2},", "");
    mvc.perform(
            post("/api/public/m/" + SLUG + "/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(onlyOther))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("promotion_not_applicable"));
  }

  @Test
  void promotionMustBeLinkedToThisStoresProductsOrServices() {
    PromotionEntity none = draft();
    assertThatThrownBy(() -> promotionService.create(tenantId, none)).hasMessage("promotion_items_required");

    UUID foreignProduct = UUID.randomUUID();
    ProductEntity f = new ProductEntity();
    f.id = foreignProduct;
    f.tenantId = otherTenantId;
    f.name = "Other store item";
    f.category = "Goods";
    f.priceZar = new BigDecimal("10.00");
    f.imageUrl = "";
    f.imagePath = "";
    f.stock = 1;
    f.createdAt = Instant.now();
    productRepository.save(f);
    PromotionEntity foreign = draft();
    foreign.applicableItemIds = foreignProduct.toString();
    assertThatThrownBy(() -> promotionService.create(tenantId, foreign)).hasMessage("promotion_item_not_found");

    PromotionEntity ok = draft();
    ok.applicableItemIds = productId + ", " + productId;
    PromotionEntity saved = promotionService.create(tenantId, ok);
    assertThat(saved.applicableItemIds).isEqualTo(productId.toString());
    assertThat(promotionService.linkableItems(tenantId)).extracting(m -> m.get("id")).contains(productId.toString());
  }

  private static PromotionEntity draft() {
    PromotionEntity p = new PromotionEntity();
    p.title = "Linked";
    p.discountType = "PERCENTAGE";
    p.discountValue = 10d;
    p.startDate = LocalDate.now();
    p.endDate = LocalDate.now().plusDays(1);
    p.active = true;
    return p;
  }

  @Test
  void promotionUseIsHeldByUnpaidOrderAndGivenBackWhenItExpires() throws Exception {
    PromotionEntity promo = promo(tenantId, "PERCENTAGE", 10d, LocalDate.now(), LocalDate.now().plusDays(5));
    promo.usageLimit = 1;
    promotionRepository.save(promo);

    JsonNode first = placeOrder(promo.id.toString(), 201);
    assertThat(promotionRepository.findById(promo.id).orElseThrow().usageCount).isEqualTo(1);
    assertThat(placeOrder(promo.id.toString(), 400).get("error").asText()).isEqualTo("promotion_exhausted");

    int expired = expireAsIf(Duration.ofDays(4));
    assertThat(expired).isEqualTo(1);
    OrderEntity o = orderRepository.findById(UUID.fromString(first.get("orderId").asText())).orElseThrow();
    assertThat(o.status).isEqualTo(OrderEntity.OrderStatus.cancelled);
    assertThat(o.expiredAt).isNotNull();
    assertThat(promotionRepository.findById(promo.id).orElseThrow().usageCount).isZero();

    placeOrder(promo.id.toString(), 201);
  }

  @Test
  void unpaidOrdersStopReservingStockOnceExpired() throws Exception {
    ProductEntity p = productRepository.findById(productId).orElseThrow();
    p.stock = 2;
    productRepository.save(p);

    placeOrder(null, 201); // reserves both units
    assertThat(placeOrder(null, 400).get("error").asText()).isEqualTo("insufficient_stock");

    assertThat(expireAsIf(Duration.ofHours(1))).isZero(); // cash orders get 72h
    assertThat(expireAsIf(Duration.ofDays(4))).isEqualTo(1);
    placeOrder(null, 201);
  }

  @Test
  void stockIsTakenAtomicallyAndNeverGoesNegative() {
    ProductEntity p = productRepository.findById(productId).orElseThrow();
    p.stock = 2;
    productRepository.save(p);

    assertThat(deduct(tenantId, 3)).isZero();
    assertThat(deduct(tenantId, 2)).isEqualTo(1);
    assertThat(deduct(tenantId, 1)).isZero();
    assertThat(deduct(otherTenantId, 0)).isZero(); // another store cannot touch this product
    assertThat(productRepository.findById(productId).orElseThrow().stock).isZero();
  }

  @Test
  void perKmDeliveryNeedsAPinAndChargesAtLeastTheMinimum() throws Exception {
    ShopSettingsEntity s = shopSettingsRepository.findByTenantId(tenantId).orElseThrow();
    s.deliveryFeeMode = "per_km";
    s.storeLat = -26.0;
    s.storeLng = 28.0;
    s.deliveryFeePerKmZar = new BigDecimal("10.00");
    s.deliveryFeeFlatZar = new BigDecimal("30.00");
    shopSettingsRepository.save(s);

    assertThat(placeDelivery(null, null, 400).get("error").asText()).isEqualTo("delivery_location_required");

    // A pin dropped on the store itself still pays the minimum.
    JsonNode atStore = placeDelivery(-26.0, 28.0, 201);
    OrderEntity near = orderRepository.findById(UUID.fromString(atStore.get("orderId").asText())).orElseThrow();
    assertThat(near.deliveryFeeZar).isEqualByComparingTo("30.00");
    assertThat(near.deliveryDistanceKm).isEqualTo(0.0);

    // 0.1° of latitude is about 11.1 km.
    JsonNode far = placeDelivery(-26.1, 28.0, 201);
    OrderEntity farOrder = orderRepository.findById(UUID.fromString(far.get("orderId").asText())).orElseThrow();
    assertThat(farOrder.deliveryFeeZar).isBetween(new BigDecimal("110.00"), new BigDecimal("112.00"));
    assertThat(farOrder.deliveryDistanceKm).isEqualTo(11.1);
  }

  private int deduct(UUID tenant, int qty) {
    Integer rows = tx.execute(s -> productRepository.deductStock(tenant, productId, qty));
    return rows == null ? 0 : rows;
  }

  private int expireAsIf(Duration later) {
    return checkoutService.expireStaleUnpaidOrders(
        Instant.now().plus(later), Duration.ofHours(2), Duration.ofHours(48), Duration.ofHours(72));
  }

  private JsonNode placeDelivery(Double lat, Double lng, int expectedStatus) throws Exception {
    String pin = lat == null ? "" : ",\"deliveryLat\":" + lat + ",\"deliveryLng\":" + lng;
    String body =
        "{\"customerName\":\"Pat Shopper\",\"customerEmail\":\"pat@shopper.test\","
            + "\"customerPhone\":\"0820000000\",\"deliveryType\":\"delivery\","
            + "\"deliveryAddress\":\"12 Long Street, Cape Town\","
            + "\"paymentMethod\":\"cash_store\""
            + pin
            + ",\"items\":[{\"product_id\":\""
            + productId
            + "\",\"quantity\":1}]}";
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
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    shopSettingsRepository.save(s);
    subscriptions.forceActivatePlan(t.id, TenantEntity.SubscriptionPlan.STANDARD);
    return t.id;
  }
}
