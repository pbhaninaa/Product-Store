package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderItemEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.OrderReviewRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderLifecycleApiIntegrationTest {

  private static final String SLUG = "lifecycle-shop";

  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired ShopSettingsRepository shopSettingsRepository;
  @Autowired ProductRepository productRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderReviewRepository orderReviewRepository;
  @Autowired OrderItemRepository orderItemRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;
  @Autowired MerchantSubscriptionService subscriptions;

  private UUID tenantId;
  private UserEntity merchantUser;
  private UUID orderId;

  @BeforeEach
  void seed() {
    orderReviewRepository.deleteAll();
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    productRepository.deleteAll();
    shopSettingsRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Lifecycle Shop";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    tenantId = t.id;

    merchantUser = new UserEntity();
    merchantUser.id = UUID.randomUUID();
    merchantUser.email = "merchant-life@test.local";
    merchantUser.passwordHash = passwordHasher.hash("Secret@123456");
    merchantUser.createdAt = Instant.now();
    userRepository.save(merchantUser);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = merchantUser.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    membershipRepository.save(m);

    Instant now = Instant.now();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = "";
    s.bankAccountHolder = "";
    s.bankAccountNumber = "";
    s.bankBranchCode = "";
    s.storeName = "Lifecycle";
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

    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.name = "Widget";
    p.category = "Goods";
    p.priceZar = new BigDecimal("25.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 3;
    p.createdAt = now;
    productRepository.save(p);

    orderId = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = orderId;
    o.tenantId = tenantId;
    o.createdAt = now;
    o.customerName = "Pat Client";
    o.customerEmail = "pat@client.test";
    o.customerPhone = "0820000000";
    o.deliveryType = OrderEntity.DeliveryType.pickup;
    o.deliveryAddress = null;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.status = OrderEntity.OrderStatus.paid;
    o.fulfillmentStatus = OrderEntity.FulfillmentStatus.processing;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    o.paymentConfirmedAt = now;
    o.subtotalZar = new BigDecimal("25.00");
    o.totalZar = new BigDecimal("25.00");
    orderRepository.save(o);

    OrderItemEntity line = new OrderItemEntity();
    line.id = UUID.randomUUID();
    line.tenantId = tenantId;
    line.orderId = orderId;
    line.productId = p.id;
    line.quantity = 1;
    line.unitPriceZar = new BigDecimal("25.00");
    line.lineTotalZar = new BigDecimal("25.00");
    orderItemRepository.save(line);
  }

  private String bearer() {
    return jwtService.mintToken(
        merchantUser.id, merchantUser.email, List.of(Role.MERCHANT_OWNER), tenantId, SLUG);
  }

  @Test
  void merchantAdvancesFulfillmentThenCustomerTracksAndReviews() throws Exception {
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/fulfillment")
                .header("Authorization", "Bearer " + bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ready\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fulfillmentStatus").value("ready"));

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/fulfillment")
                .header("Authorization", "Bearer " + bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"completed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fulfillmentStatus").value("completed"));

    mvc.perform(
            get("/api/public/m/" + SLUG + "/checkout/orders/" + orderId)
                .param("customerEmail", "pat@client.test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("paid"))
        .andExpect(jsonPath("$.fulfillmentStatus").value("completed"))
        .andExpect(jsonPath("$.customer_name").value("Pat Client"))
        .andExpect(jsonPath("$.order_items[0].products.name").value("Widget"));

    mvc.perform(
            post("/api/public/m/" + SLUG + "/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"kind\":\"order\",\"id\":\""
                        + orderId
                        + "\",\"customerEmail\":\"pat@client.test\",\"rating\":5,\"comment\":\"Great\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mvc.perform(get("/api/public/m/" + SLUG + "/reviews/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewCount").value(1));

    mvc.perform(get("/api/public/faqs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections").isArray());

    assertThat(orderRepository.findById(orderId).orElseThrow().fulfillmentStatus)
        .isEqualTo(OrderEntity.FulfillmentStatus.completed);
  }

  @Test
  void fulfillmentRejectedUntilPaid() throws Exception {
    OrderEntity pending = orderRepository.findById(orderId).orElseThrow();
    pending.status = OrderEntity.OrderStatus.pending_payment;
    pending.fulfillmentStatus = null;
    orderRepository.save(pending);

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/fulfillment")
                .header("Authorization", "Bearer " + bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ready\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("order_not_paid"));
  }
}
