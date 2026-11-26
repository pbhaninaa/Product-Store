package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Manual EFT was removed (CTO, 2026-11-23): new EFT orders and bookings are refused and shops no longer advertise
 * EFT or their bank details, while EFT rows placed before the change stay visible and can still be finished.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualEftRemovalIntegrationTest {

  private static final String SLUG = "no-eft-shop";
  /** A PNG header padded past the 32 byte minimum a proof needs before it can be approved. */
  private static final byte[] PNG_PROOF = pngProof();

  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired ProductRepository products;
  @Autowired OrderRepository orders;
  @Autowired OrderItemRepository orderItems;
  @Autowired SalonServiceRepository services;
  @Autowired SalonBookingRepository bookings;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  private UUID tenantId;
  private UserEntity owner;
  private UUID productId;
  private UUID serviceId;

  @BeforeEach
  void seed() {
    cleanup();
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "No EFT Shop";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    tenantId = t.id;

    owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-no-eft@test.local";
    owner.passwordHash = passwordHasher.hash("Owner@123456");
    owner.createdAt = Instant.now();
    users.save(owner);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = owner.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    memberships.save(m);

    shopSettings.save(settingsWithLegacyEftOn());
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

    SalonServiceEntity s = new SalonServiceEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.name = "Haircut";
    s.description = "";
    s.durationMinutes = 60;
    s.priceZar = new BigDecimal("150.00");
    s.active = true;
    s.createdAt = Instant.now();
    services.save(s);
    serviceId = s.id;
  }

  @AfterEach
  void cleanup() {
    orderItems.deleteAll();
    orders.deleteAll();
    products.deleteAll();
    bookings.deleteAll();
    services.deleteAll();
    shopSettings.deleteAll();
    subscriptionRepository.deleteAll();
    memberships.deleteAll();
    users.deleteAll();
    tenants.findBySlug(SLUG).ifPresent(tenants::delete);
  }

  @Test
  void checkoutRefusesManualEftEvenWhenTheShopsLegacyToggleIsOn() throws Exception {
    mvc.perform(
            post("/api/public/m/" + SLUG + "/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerName\":\"Pat Client\",\"customerEmail\":\"pat@client.test\","
                        + "\"customerPhone\":\"0820000000\",\"deliveryType\":\"pickup\","
                        + "\"paymentMethod\":\"eft\",\"items\":[{\"product_id\":\""
                        + productId
                        + "\",\"quantity\":1}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("eft_not_accepted"));

    assertThat(orders.findAllByTenant(tenantId)).isEmpty();
  }

  @Test
  void salonBookingRefusesManualEftEvenForAShopWithOneOtherMethod() throws Exception {
    // A one-method shop used to ignore the requested method; asking for EFT must not become a cash booking.
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElseThrow();
    s.acceptCustomerOnline = false;
    shopSettings.save(s);

    mvc.perform(
            post("/api/public/m/" + SLUG + "/salon/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"serviceId\":\""
                        + serviceId
                        + "\",\"startAt\":\""
                        + Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
                        + "\",\"customerName\":\"Pat Client\",\"customerPhone\":\"0820000000\","
                        + "\"customerEmail\":\"pat@client.test\",\"paymentMethod\":\"eft\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("eft_not_accepted"));

    assertThat(bookings.findAll()).isEmpty();
  }

  @Test
  void publicShopSettingsNoLongerAdvertiseEftOrExposeBankDetails() throws Exception {
    mvc.perform(get("/api/public/m/" + SLUG + "/shop-settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptCustomerEft").value(false))
        .andExpect(jsonPath("$.acceptCustomerCash").value(true))
        .andExpect(jsonPath("$.eftBankInstructions").doesNotExist())
        .andExpect(jsonPath("$.bankName").doesNotExist())
        .andExpect(jsonPath("$.bankAccountHolder").doesNotExist())
        .andExpect(jsonPath("$.bankAccountNumber").doesNotExist())
        .andExpect(jsonPath("$.bankBranchCode").doesNotExist());
  }

  @Test
  void adminStoreSettingsDropTheEftToggleButKeepPayoutBankDetails() throws Exception {
    mvc.perform(get("/api/m/" + SLUG + "/admin/store/settings").header("Authorization", bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bankAccountNumber").value(""))
        .andExpect(jsonPath("$.accountNumberMasked").value("•••• 0000"))
        .andExpect(jsonPath("$.acceptCustomerEft").doesNotExist())
        .andExpect(jsonPath("$.eftBankInstructions").doesNotExist());

    // EFT no longer counts as a payment option: online and cash both off is refused, whatever EFT says.
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/store/banking")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"acceptCustomerOnline\":false,\"acceptCustomerCash\":false,\"acceptCustomerEft\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("payment_options_required"));

    mvc.perform(
            put("/api/m/" + SLUG + "/admin/store/banking")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankAccountNumber\":\"62111111111\",\"eftBankInstructions\":\"Pay to us\"}"))
        .andExpect(status().isOk());
    ShopSettingsEntity after = shopSettings.findByTenantId(tenantId).orElseThrow();
    assertThat(after.bankAccountNumber).isEqualTo("62111111111");
    assertThat(after.eftBankInstructions).isEqualTo("Old EFT text");
  }

  @Test
  void legacyEftOrderAwaitingReviewIsStillListedAndCanBeConfirmed() throws Exception {
    UUID orderId = legacyEftOrder(OrderEntity.PaymentVerificationState.manual_pending, PNG_PROOF);

    mvc.perform(get("/api/m/" + SLUG + "/admin/orders").header("Authorization", bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders[0].id").value(orderId.toString()))
        .andExpect(jsonPath("$.orders[0].paymentMethod").value("eft"))
        .andExpect(jsonPath("$.orders[0].paymentVerificationState").value("manual_pending"));

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/confirm-payment")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(orders.findById(orderId).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.paid);
  }

  @Test
  void legacyEftOrderStillAwaitingProofCanStillSendIt() throws Exception {
    UUID orderId = legacyEftOrder(OrderEntity.PaymentVerificationState.awaiting_proof, null);

    mvc.perform(
            multipart("/api/public/m/" + SLUG + "/checkout/orders/" + orderId + "/eft-proof")
                .file(new MockMultipartFile("proof", "slip.png", "image/png", PNG_PROOF))
                .param("customerEmail", "pat@client.test")
                .param("bankReference", "REF-" + orderId.toString().substring(0, 8)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentVerificationState").value("manual_pending"));
  }

  @Test
  void legacyEftBookingAwaitingReviewCanStillBeApprovedOrRejected() throws Exception {
    UUID approveId = legacyEftBooking(tenantId);
    UUID rejectId = legacyEftBooking(tenantId);

    mvc.perform(get("/api/m/" + SLUG + "/admin/salon/bookings").header("Authorization", bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].clientPaymentMethod").value("eft"))
        .andExpect(jsonPath("$[0].paymentVerificationState").value("manual_pending"));

    decide(approveId, "approve").andExpect(status().isOk());
    decide(rejectId, "reject").andExpect(status().isOk());

    SalonBookingEntity approved = bookings.findById(approveId).orElseThrow();
    assertThat(approved.status).isEqualTo(SalonBookingEntity.Status.confirmed);
    assertThat(approved.paymentVerificationState)
        .isEqualTo(SalonBookingEntity.PaymentVerificationState.manual_approved);
    assertThat(bookings.findById(rejectId).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.cancelled);
  }

  @Test
  void legacyEftBookingOfAnotherShopCannotBeDecided() throws Exception {
    UUID otherShopsBooking = legacyEftBooking(UUID.randomUUID());

    decide(otherShopsBooking, "approve")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
    assertThat(bookings.findById(otherShopsBooking).orElseThrow().paymentVerificationState)
        .isEqualTo(SalonBookingEntity.PaymentVerificationState.manual_pending);
  }

  @Test
  void staffCannotReadTheOwnersPayoutBankDetails() throws Exception {
    UserEntity staff = new UserEntity();
    staff.id = UUID.randomUUID();
    staff.email = "staff-no-eft@test.local";
    staff.passwordHash = passwordHasher.hash("Staff@123456");
    staff.createdAt = Instant.now();
    users.save(staff);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = staff.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_STAFF;
    m.createdAt = Instant.now();
    memberships.save(m);
    String staffBearer =
        "Bearer " + jwtService.mintToken(staff.id, staff.email, List.of(Role.MERCHANT_STAFF), tenantId, SLUG);

    mvc.perform(get("/api/m/" + SLUG + "/admin/store/settings").header("Authorization", staffBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.storeName").value("No EFT Shop"))
        .andExpect(jsonPath("$.bankName").value(""))
        .andExpect(jsonPath("$.bankAccountHolder").value(""))
        .andExpect(jsonPath("$.bankAccountNumber").value(""))
        .andExpect(jsonPath("$.bankBranchCode").value(""));

    // Blanking the response must never touch the stored details.
    assertThat(shopSettings.findByTenantId(tenantId).orElseThrow().bankAccountNumber).isEqualTo("62000000000");
    mvc.perform(get("/api/m/" + SLUG + "/admin/store/settings").header("Authorization", bearer()))
        .andExpect(jsonPath("$.bankAccountNumber").value(""))
        .andExpect(jsonPath("$.accountNumberMasked").value("•••• 0000"))
        .andExpect(jsonPath("$.bankBranchCode").value("250655"));
  }

  @Test
  void legacyEftBookingProofCannotReopenACancelledOrPastBooking() throws Exception {
    Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
    UUID cancelled = legacyEftBookingAwaitingProof(SalonBookingEntity.Status.cancelled, tomorrow);
    UUID past = legacyEftBookingAwaitingProof(SalonBookingEntity.Status.pending, Instant.now().minus(1, ChronoUnit.HOURS));
    UUID open = legacyEftBookingAwaitingProof(SalonBookingEntity.Status.pending, tomorrow);

    sendBookingProof(cancelled)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("booking_not_pending"));
    sendBookingProof(past)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("booking_not_pending"));
    SalonBookingEntity stillCancelled = bookings.findById(cancelled).orElseThrow();
    assertThat(stillCancelled.status).isEqualTo(SalonBookingEntity.Status.cancelled);
    assertThat(stillCancelled.paymentVerificationState)
        .isEqualTo(SalonBookingEntity.PaymentVerificationState.awaiting_proof);

    sendBookingProof(open)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentVerificationState").value("manual_pending"))
        .andExpect(jsonPath("$.bookingStatus").value("pending"));
  }

  @Test
  void legacyEftBookingThatWasCancelledCannotBeApproved() throws Exception {
    UUID id = legacyEftBooking(tenantId);
    SalonBookingEntity b = bookings.findById(id).orElseThrow();
    b.status = SalonBookingEntity.Status.cancelled;
    bookings.save(b);

    decide(id, "approve")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("booking_not_pending"));
    assertThat(bookings.findById(id).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.cancelled);
  }

  private org.springframework.test.web.servlet.ResultActions sendBookingProof(UUID bookingId) throws Exception {
    return mvc.perform(
        multipart("/api/public/m/" + SLUG + "/salon/bookings/" + bookingId + "/eft-proof")
            .file(new MockMultipartFile("proof", "slip.png", "image/png", PNG_PROOF))
            .param("customerEmail", "pat@client.test")
            .param("bankReference", "REF-" + bookingId.toString().substring(0, 8)));
  }

  /** A legacy EFT booking that never received its proof. */
  private UUID legacyEftBookingAwaitingProof(SalonBookingEntity.Status status, Instant start) {
    UUID id = legacyEftBooking(tenantId);
    SalonBookingEntity b = bookings.findById(id).orElseThrow();
    b.status = status;
    b.startAt = start;
    b.endAt = start.plus(60, ChronoUnit.MINUTES);
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.awaiting_proof;
    b.paymentProofData = null;
    b.paymentProofContentType = null;
    b.paymentProofPath = "";
    b.paymentReferenceDeclared = "";
    bookings.save(b);
    return id;
  }

  private org.springframework.test.web.servlet.ResultActions decide(UUID bookingId, String decision)
      throws Exception {
    return mvc.perform(
        post("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId + "/eft-payment-decision")
            .header("Authorization", bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"" + decision + "\"}"));
  }

  private String bearer() {
    return "Bearer "
        + jwtService.mintToken(owner.id, owner.email, List.of(Role.MERCHANT_OWNER), tenantId, SLUG);
  }

  /** An EFT order placed before Manual EFT was removed, with one line of the seeded product. */
  private UUID legacyEftOrder(OrderEntity.PaymentVerificationState state, byte[] proof) {
    UUID id = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = id;
    o.tenantId = tenantId;
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
      o.paymentProofContentType = "image/png";
      o.paymentProofPath = "";
      o.paymentReferenceDeclared = "REF123";
    }
    o.subtotalZar = new BigDecimal("25.00");
    o.totalZar = new BigDecimal("25.00");
    orders.save(o);

    OrderItemEntity line = new OrderItemEntity();
    line.id = UUID.randomUUID();
    line.tenantId = tenantId;
    line.orderId = id;
    line.productId = productId;
    line.quantity = 1;
    line.unitPriceZar = new BigDecimal("25.00");
    line.lineTotalZar = new BigDecimal("25.00");
    orderItems.save(line);
    return id;
  }

  /** An EFT booking made before Manual EFT was removed, whose proof is waiting for the merchant. */
  private UUID legacyEftBooking(UUID ownerTenantId) {
    Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = ownerTenantId;
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

  private ShopSettingsEntity settingsWithLegacyEftOn() {
    Instant now = Instant.now();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "Old EFT text";
    s.bankName = "Test Bank";
    s.bankAccountHolder = "No EFT Shop";
    s.bankAccountNumber = "62000000000";
    s.bankBranchCode = "250655";
    s.storeName = "No EFT Shop";
    s.contactEmail = "shop@test.local";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "salon_and_store";
    s.openingHoursJson = "[]";
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    return s;
  }

  private static byte[] pngProof() {
    byte[] b = new byte[64];
    byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    System.arraycopy(sig, 0, b, 0, sig.length);
    return b;
  }
}
