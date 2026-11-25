package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderItemEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.ShopSettingsDefaults;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Independent QA for the Manual EFT removal (CTO, 2026-11-23): input variants of the refused method, a legacy shop
 * that only had EFT on, cash still working, authorization on the legacy review endpoints, the order expiry of legacy
 * EFT rows and the Paystack prompt after a paid period ends.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualEftRemovalQaIntegrationTest {

  private static final String SLUG = "qa-eft-shop";
  private static final String OTHER_SLUG = "qa-eft-other";
  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
  private static final byte[] PNG_PROOF = proof(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
  /** PDF magic with no readable text: the approval check cannot find the amount or reference in it. */
  private static final byte[] UNREADABLE_PDF_PROOF = proof(new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'});

  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired ProductRepository products;
  @Autowired OrderRepository orders;
  @Autowired OrderItemRepository orderItems;
  @Autowired SalonServiceRepository services;
  @Autowired SalonStaffRepository salonStaff;
  @Autowired SalonStaffAvailabilityRepository salonAvailability;
  @Autowired SalonBookingRepository bookings;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired CheckoutService checkout;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  private UUID tenantId;
  private UUID otherTenantId;
  private UserEntity owner;
  private UserEntity otherOwner;
  private UUID productId;
  private UUID serviceId;

  @BeforeEach
  void seed() {
    cleanup();
    tenantId = tenant(SLUG);
    otherTenantId = tenant(OTHER_SLUG);
    owner = owner("owner-qa-eft@test.local", tenantId);
    otherOwner = owner("owner-qa-other@test.local", otherTenantId);

    ShopSettingsEntity s = ShopSettingsDefaults.newRowForTenant(tenantId);
    s.shopType = "salon_and_store";
    s.openingHoursJson = null;
    s.bankName = "Test Bank";
    s.bankAccountHolder = "QA Shop";
    s.bankAccountNumber = "62000000000";
    s.bankBranchCode = "250655";
    s.acceptCustomerEft = true; // legacy column left on by the shop before the change
    shopSettings.save(s);
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.name = "Widget";
    p.category = "Goods";
    p.priceZar = new BigDecimal("25.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 5;
    p.createdAt = Instant.now();
    products.save(p);
    productId = p.id;

    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = UUID.randomUUID();
    svc.tenantId = tenantId;
    svc.name = "Haircut";
    svc.description = "";
    svc.durationMinutes = 60;
    svc.priceZar = new BigDecimal("150.00");
    svc.active = true;
    svc.createdAt = Instant.now();
    services.save(svc);
    serviceId = svc.id;

    SalonStaffEntity stylist = new SalonStaffEntity();
    stylist.id = UUID.randomUUID();
    stylist.tenantId = tenantId;
    stylist.displayName = "Stylist";
    stylist.active = true;
    stylist.createdAt = Instant.now();
    salonStaff.save(stylist);
    for (int day = 1; day <= 7; day++) {
      SalonStaffAvailabilityEntity w = new SalonStaffAvailabilityEntity();
      w.id = UUID.randomUUID();
      w.tenantId = tenantId;
      w.staffId = stylist.id;
      w.dayOfWeek = day;
      w.startTime = LocalTime.of(8, 0);
      w.endTime = LocalTime.of(18, 0);
      w.createdAt = Instant.now();
      salonAvailability.save(w);
    }
  }

  @AfterEach
  void cleanup() {
    orderItems.deleteAll();
    orders.deleteAll();
    products.deleteAll();
    bookings.deleteAll();
    salonAvailability.deleteAll();
    salonStaff.deleteAll();
    services.deleteAll();
    shopSettings.deleteAll();
    subscriptionRepository.deleteAll();
    memberships.deleteAll();
    users.deleteAll();
    tenants.findBySlug(SLUG).ifPresent(tenants::delete);
    tenants.findBySlug(OTHER_SLUG).ifPresent(tenants::delete);
  }

  // ---- AC1: no new Manual EFT, whatever the spelling ----

  @ParameterizedTest
  @ValueSource(strings = {"eft", "EFT", " Eft "})
  void checkoutRefusesManualEftInAnySpellingAndHoldsNoStock(String method) throws Exception {
    placeOrder(method, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("eft_not_accepted"));

    assertThat(orders.findAllByTenant(tenantId)).isEmpty();
    assertThat(orderItems.sumPendingQtyForProduct(tenantId, productId)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"eft", "EFT"})
  void salonBookingRefusesManualEftForAShopOfferingCashAndOnline(String method) throws Exception {
    book(method)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("eft_not_accepted"));

    assertThat(bookings.findAll()).isEmpty();
  }

  // ---- A legacy shop that had only Manual EFT on (cash and online both off) ----

  @ParameterizedTest
  @CsvSource({
    "cash_store, , cash_not_accepted",
    "online, CARD, online_payment_not_accepted",
    "eft, , eft_not_accepted"
  })
  void legacyEftOnlyShopTakesNoCheckoutPayment(String method, String channel, String error) throws Exception {
    turnOffCashAndOnline();

    placeOrder(method, channel).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(error));

    assertThat(orders.findAllByTenant(tenantId)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"eft", "cash_store", "online", ""})
  void legacyEftOnlyShopTakesNoBookingPayment(String method) throws Exception {
    turnOffCashAndOnline();

    book(method)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("payment_not_configured"));

    assertThat(bookings.findAll()).isEmpty();
  }

  // ---- AC5: cash still works and responses carry no EFT fields ----

  @Test
  void cashCheckoutStillIssuesAPaymentCodeAndNoEftField() throws Exception {
    placeOrder("cash_store", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.needsCashPaymentCode").value(true))
        .andExpect(jsonPath("$.cashPaymentCode").isNotEmpty())
        .andExpect(jsonPath("$.needsEftProof").doesNotExist());

    OrderEntity o = orders.findAllByTenant(tenantId).get(0);
    assertThat(o.paymentMethod).isEqualTo(OrderEntity.PaymentMethod.cash_store);
    assertThat(o.paymentVerificationState).isEqualTo(OrderEntity.PaymentVerificationState.not_applicable);
  }

  @Test
  void cashSalonBookingStillIssuesAPaymentCodeAndNoEftField() throws Exception {
    book("cash_store")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentMethod").value("cash_store"))
        .andExpect(jsonPath("$.cashPaymentCode").isNotEmpty())
        .andExpect(jsonPath("$.needsEftProof").doesNotExist());

    SalonBookingEntity b = bookings.findAll().get(0);
    assertThat(b.clientPaymentMethod).isEqualTo(SalonBookingEntity.ClientPaymentMethod.cash_store);
    assertThat(b.paymentVerificationState).isEqualTo(SalonBookingEntity.PaymentVerificationState.not_applicable);
  }

  // ---- AC3: payout details stay private ----

  @Test
  void adminStoreSettingsWithTheBankDetailsNeedASignedInMember() throws Exception {
    mvc.perform(get("/api/m/" + SLUG + "/admin/store/settings"))
        .andExpect(status().is(anyOf(is(401), is(403))))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("62000000000"))));

    mvc.perform(get("/api/m/" + SLUG + "/admin/store/settings").header("Authorization", bearer(otherOwner, otherTenantId, OTHER_SLUG)))
        .andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("62000000000"))));
  }

  // ---- AC4 + authorization: legacy review endpoints ----

  @Test
  void ownerOfAnotherShopCannotConfirmALegacyEftOrder() throws Exception {
    UUID orderId = legacyEftOrder(tenantId, OrderEntity.PaymentVerificationState.manual_pending, PNG_PROOF);

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/confirm-payment")
                .header("Authorization", bearer(otherOwner, otherTenantId, OTHER_SLUG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    assertThat(orders.findById(orderId).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  @Test
  void legacyEftOrderOfAnotherShopCannotBeConfirmedThroughOwnShop() throws Exception {
    UUID foreignOrder = legacyEftOrder(otherTenantId, OrderEntity.PaymentVerificationState.manual_pending, PNG_PROOF);

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + foreignOrder + "/confirm-payment")
                .header("Authorization", bearer(owner, tenantId, SLUG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(status().isOk());

    assertThat(orders.findById(foreignOrder).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"approve", "reject"})
  void ownerOfAnotherShopCannotDecideALegacyEftBooking(String decision) throws Exception {
    UUID bookingId = legacyEftBooking();

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId + "/eft-payment-decision")
                .header("Authorization", bearer(otherOwner, otherTenantId, OTHER_SLUG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}"))
        .andExpect(status().isForbidden());

    SalonBookingEntity b = bookings.findById(bookingId).orElseThrow();
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
    assertThat(b.paymentVerificationState).isEqualTo(SalonBookingEntity.PaymentVerificationState.manual_pending);
  }

  @Test
  void anonymousCallerCannotConfirmOrDecideLegacyEft() throws Exception {
    UUID orderId = legacyEftOrder(tenantId, OrderEntity.PaymentVerificationState.manual_pending, PNG_PROOF);
    UUID bookingId = legacyEftBooking();

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/confirm-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().is(anyOf(is(401), is(403))));
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId + "/eft-payment-decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\"}"))
        .andExpect(status().is(anyOf(is(401), is(403))));

    assertThat(orders.findById(orderId).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
    assertThat(bookings.findById(bookingId).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.pending);
  }

  @Test
  void legacyPdfProofTheCheckCannotMatchIsRefusedWithItsOwnCode() throws Exception {
    UUID orderId = legacyEftOrder(tenantId, OrderEntity.PaymentVerificationState.manual_pending, UNREADABLE_PDF_PROOF);

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/confirm-payment")
                .header("Authorization", bearer(owner, tenantId, SLUG))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("eft_proof_amount_or_reference_mismatch"));
  }

  // ---- AC5: order expiry of legacy EFT rows unchanged ----

  @Test
  void legacyEftOrderAwaitingProofStillExpiresButOneAwaitingReviewDoesNot() {
    UUID awaitingProof = legacyEftOrder(tenantId, OrderEntity.PaymentVerificationState.awaiting_proof, null);
    UUID awaitingReview = legacyEftOrder(tenantId, OrderEntity.PaymentVerificationState.manual_pending, PNG_PROOF);
    Instant now = Instant.now().plus(49, ChronoUnit.HOURS);

    checkout.expireStaleUnpaidOrders(now, Duration.ofHours(2), Duration.ofHours(48), Duration.ofHours(72));

    assertThat(orders.findById(awaitingProof).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.cancelled);
    assertThat(orders.findById(awaitingReview).orElseThrow().status)
        .isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  // ---- AC2: an ended paid period asks for Paystack payment ----

  @Test
  void endedPaidPeriodAsksForPaystackPaymentAlthoughTheLastProofWasApproved() throws Exception {
    MerchantSubscriptionEntity sub = subscriptionRepository.findByTenantId(tenantId).orElseThrow();
    sub.trialStartAt = Instant.now().minus(60, ChronoUnit.DAYS);
    sub.trialEndAt = Instant.now().minus(53, ChronoUnit.DAYS);
    sub.trialDatesBackfilled = true;
    sub.onTrial = false;
    sub.periodStart = LocalDate.now(ZONE).minusDays(40);
    sub.periodEnd = LocalDate.now(ZONE).minusDays(10);
    subscriptionRepository.save(sub);

    mvc.perform(get("/api/m/" + SLUG + "/admin/subscription/me").header("Authorization", bearer(owner, tenantId, SLUG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.paymentProofStatus").value("APPROVED"))
        .andExpect(jsonPath("$.needsPayment").value(true));
  }

  // ---- helpers ----

  private ResultActions placeOrder(String method, String channel) throws Exception {
    String channelJson = channel == null ? "" : ",\"onlinePaymentChannel\":\"" + channel + "\"";
    return mvc.perform(
        post("/api/public/m/" + SLUG + "/checkout/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"customerName\":\"Pat Client\",\"customerEmail\":\"pat@client.test\","
                    + "\"customerPhone\":\"0820000000\",\"deliveryType\":\"pickup\","
                    + "\"paymentMethod\":\""
                    + method
                    + "\""
                    + channelJson
                    + ",\"items\":[{\"product_id\":\""
                    + productId
                    + "\",\"quantity\":1}]}"));
  }

  private ResultActions book(String method) throws Exception {
    Instant start = LocalDate.now(ZONE).plusDays(2).atTime(10, 0).atZone(ZONE).toInstant();
    return mvc.perform(
        post("/api/public/m/" + SLUG + "/salon/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"serviceId\":\""
                    + serviceId
                    + "\",\"startAt\":\""
                    + start
                    + "\",\"customerName\":\"Pat Client\",\"customerPhone\":\"0820000000\","
                    + "\"customerEmail\":\"pat@client.test\",\"paymentMethod\":\""
                    + method
                    + "\"}"));
  }

  private void turnOffCashAndOnline() {
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElseThrow();
    s.acceptCustomerCash = false;
    s.acceptCustomerOnline = false;
    s.acceptCustomerEft = true;
    shopSettings.save(s);
  }

  private UUID tenant(String slug) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = slug;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    return t.id;
  }

  private UserEntity owner(String email, UUID tid) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash("Owner@123456");
    u.createdAt = Instant.now();
    users.save(u);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = tid;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    memberships.save(m);
    return u;
  }

  private String bearer(UserEntity u, UUID tid, String slug) {
    return "Bearer " + jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), tid, slug);
  }

  /** An EFT order placed before Manual EFT was removed. */
  private UUID legacyEftOrder(UUID tid, OrderEntity.PaymentVerificationState state, byte[] proof) {
    UUID id = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = id;
    o.tenantId = tid;
    o.createdAt = Instant.now();
    o.customerName = "Pat Client";
    o.customerEmail = "pat@client.test";
    o.customerPhone = "0820000000";
    o.deliveryType = OrderEntity.DeliveryType.pickup;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.eft;
    o.status = OrderEntity.OrderStatus.pending_payment;
    o.paymentVerificationState = state;
    if (proof != null) {
      o.paymentProofData = proof;
      o.paymentProofContentType = proof == PNG_PROOF ? "image/png" : "application/pdf";
      o.paymentProofPath = "";
      o.paymentReferenceDeclared = "REF123";
    }
    o.subtotalZar = new BigDecimal("25.00");
    o.totalZar = new BigDecimal("25.00");
    orders.save(o);

    OrderItemEntity line = new OrderItemEntity();
    line.id = UUID.randomUUID();
    line.tenantId = tid;
    line.orderId = id;
    line.productId = productId;
    line.quantity = 1;
    line.unitPriceZar = new BigDecimal("25.00");
    line.lineTotalZar = new BigDecimal("25.00");
    orderItems.save(line);
    return id;
  }

  private UUID legacyEftBooking() {
    Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.customerName = "Pat Client";
    b.customerPhone = "0820000000";
    b.customerEmail = "pat@client.test";
    b.startAt = start;
    b.endAt = start.plus(60, ChronoUnit.MINUTES);
    b.status = SalonBookingEntity.Status.pending;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.eft;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.manual_pending;
    b.paymentProofData = PNG_PROOF;
    b.paymentProofContentType = "image/png";
    b.paymentProofPath = "";
    b.paymentReferenceDeclared = "REF123";
    b.amountZar = new BigDecimal("150.00");
    b.createdAt = Instant.now();
    bookings.save(b);
    return b.id;
  }

  private static byte[] proof(byte[] magic) {
    byte[] b = new byte[64];
    System.arraycopy(magic, 0, b, 0, magic.length);
    return b;
  }
}
