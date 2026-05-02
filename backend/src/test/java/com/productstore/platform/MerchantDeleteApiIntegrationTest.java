package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantDeleteApiIntegrationTest {

  private static final String SLUG = "del-merchant";

  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired ShopSettingsRepository shopSettingsRepository;
  @Autowired ProductRepository productRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderItemRepository orderItemRepository;
  @Autowired SalonServiceRepository salonServiceRepository;
  @Autowired SalonBookingRepository salonBookingRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  private UUID tenantId;
  private UserEntity merchantUser;

  @BeforeEach
  void reset() {
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    productRepository.deleteAll();
    salonBookingRepository.deleteAll();
    salonServiceRepository.deleteAll();
    shopSettingsRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Delete Test";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    tenantId = t.id;

    merchantUser = new UserEntity();
    merchantUser.id = UUID.randomUUID();
    merchantUser.email = "merchant-del@test.local";
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
    s.storeName = "Test";
    s.contactEmail = "a@b.co";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "salon_only";
    s.openingHoursJson = "[]";
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    shopSettingsRepository.save(s);
  }

  private String bearer() {
    return jwtService.mintToken(
        merchantUser.id, merchantUser.email, List.of(Role.MERCHANT_OWNER), tenantId, SLUG);
  }

  @Test
  void deletePendingOrder_removesRow() throws Exception {
    UUID productId = UUID.randomUUID();
    ProductEntity p = new ProductEntity();
    p.id = productId;
    p.tenantId = tenantId;
    p.name = "P";
    p.category = "c";
    p.priceZar = new BigDecimal("10.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 5;
    p.createdAt = Instant.now();
    productRepository.save(p);

    UUID orderId = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = orderId;
    o.tenantId = tenantId;
    o.createdAt = Instant.now();
    o.customerName = "A";
    o.customerEmail = "a@b.co";
    o.customerPhone = "";
    o.deliveryType = OrderEntity.DeliveryType.pickup;
    o.deliveryAddress = null;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.status = OrderEntity.OrderStatus.pending_payment;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    o.subtotalZar = new BigDecimal("10.00");
    o.totalZar = new BigDecimal("10.00");
    orderRepository.save(o);

    OrderItemEntity line = new OrderItemEntity();
    line.id = UUID.randomUUID();
    line.tenantId = tenantId;
    line.orderId = orderId;
    line.productId = productId;
    line.quantity = 1;
    line.unitPriceZar = new BigDecimal("10.00");
    line.lineTotalZar = new BigDecimal("10.00");
    orderItemRepository.save(line);

    mvc.perform(
            delete("/api/m/" + SLUG + "/admin/orders/" + orderId)
                .header("Authorization", "Bearer " + bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(orderRepository.findById(orderId)).isEmpty();
    assertThat(orderItemRepository.findAllByTenantAndOrderId(tenantId, orderId)).isEmpty();
  }

  @Test
  void deletePaidOrder_returnsNotDeletable() throws Exception {
    UUID orderId = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = orderId;
    o.tenantId = tenantId;
    o.createdAt = Instant.now();
    o.customerName = "A";
    o.customerEmail = "a@b.co";
    o.customerPhone = "";
    o.deliveryType = OrderEntity.DeliveryType.pickup;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.status = OrderEntity.OrderStatus.paid;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    o.subtotalZar = new BigDecimal("1.00");
    o.totalZar = new BigDecimal("1.00");
    o.paymentConfirmedAt = Instant.now();
    orderRepository.save(o);

    mvc.perform(
            delete("/api/m/" + SLUG + "/admin/orders/" + orderId)
                .header("Authorization", "Bearer " + bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.reason").value("not_deletable"));

    assertThat(orderRepository.findById(orderId)).isPresent();
  }

  @Test
  void deleteConfirmedBooking_returnsNotDeletable() throws Exception {
    UUID serviceId = UUID.randomUUID();
    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = serviceId;
    svc.tenantId = tenantId;
    svc.name = "Cut";
    svc.description = "";
    svc.durationMinutes = 30;
    svc.priceZar = new BigDecimal("100.00");
    svc.active = true;
    svc.createdAt = Instant.now();
    salonServiceRepository.save(svc);

    UUID bookingId = UUID.randomUUID();
    Instant start = Instant.now().plusSeconds(3600);
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = bookingId;
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.customerName = "C";
    b.customerPhone = "0123456789";
    b.customerEmail = "c@d.co";
    b.startAt = start;
    b.endAt = start.plusSeconds(1800);
    b.status = SalonBookingEntity.Status.confirmed;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    b.createdAt = Instant.now();
    salonBookingRepository.save(b);

    mvc.perform(
            delete("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId)
                .header("Authorization", "Bearer " + bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(false));

    assertThat(salonBookingRepository.findById(bookingId)).isPresent();
  }

  @Test
  void deletePendingBooking_removesRow() throws Exception {
    UUID serviceId = UUID.randomUUID();
    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = serviceId;
    svc.tenantId = tenantId;
    svc.name = "Cut";
    svc.description = "";
    svc.durationMinutes = 30;
    svc.priceZar = new BigDecimal("100.00");
    svc.active = true;
    svc.createdAt = Instant.now();
    salonServiceRepository.save(svc);

    UUID bookingId = UUID.randomUUID();
    Instant start = Instant.now().plusSeconds(7200);
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = bookingId;
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.customerName = "C";
    b.customerPhone = "0123456789";
    b.customerEmail = "c@d.co";
    b.startAt = start;
    b.endAt = start.plusSeconds(1800);
    b.status = SalonBookingEntity.Status.pending;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.eft;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.awaiting_proof;
    b.createdAt = Instant.now();
    salonBookingRepository.save(b);

    mvc.perform(
            delete("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId)
                .header("Authorization", "Bearer " + bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(salonBookingRepository.findById(bookingId)).isEmpty();
  }
}
