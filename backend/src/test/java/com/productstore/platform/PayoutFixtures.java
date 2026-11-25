package com.productstore.platform;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.Role;

import org.springframework.context.ApplicationContext;

/**
 * Seeds merchants, in-app payments (order or booking plus its payment attempt) and signed-in users for the
 * provider payout tests. Every merchant gets a unique slug, so tests never depend on each other's data.
 */
final class PayoutFixtures {
  private final TenantRepository tenants;
  private final ShopSettingsRepository shopSettings;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final OnlinePaymentAttemptRepository attempts;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final JwtService jwt;

  PayoutFixtures(ApplicationContext ctx) {
    this.tenants = ctx.getBean(TenantRepository.class);
    this.shopSettings = ctx.getBean(ShopSettingsRepository.class);
    this.orders = ctx.getBean(OrderRepository.class);
    this.bookings = ctx.getBean(SalonBookingRepository.class);
    this.attempts = ctx.getBean(OnlinePaymentAttemptRepository.class);
    this.users = ctx.getBean(UserRepository.class);
    this.memberships = ctx.getBean(MembershipRepository.class);
    this.jwt = ctx.getBean(JwtService.class);
  }

  record User(UUID id, String email, String token, ApiUserPrincipal principal) {}

  TenantEntity merchant(String name, String bankName, String accountHolder, String accountNumber, String branchCode) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "payout-" + Long.toHexString(System.nanoTime()) + "-" + t.id.toString().substring(0, 4);
    t.name = name;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);

    Instant ts = Instant.now();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = t.id;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = bankName;
    s.bankAccountHolder = accountHolder;
    s.bankAccountNumber = accountNumber;
    s.bankBranchCode = branchCode;
    s.storeName = name;
    s.contactEmail = "store@test.local";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "salon_only";
    s.openingHoursJson = "[]";
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = ts;
    s.updatedAt = ts;
    shopSettings.save(s);
    return t;
  }

  TenantEntity merchant(String name) {
    return merchant(name, "FNB", "Store Owner", "62000000001", "250655");
  }

  /** A settled in-app booking payment ({@code feeCents} null = unknown) for a booking in {@code status}. */
  OnlinePaymentAttemptEntity bookingPayment(
      UUID tenantId, long grossCents, Long feeCents, SalonBookingEntity.Status status,
      OnlinePaymentAttemptEntity.Outcome outcome, Instant paidAt) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = UUID.randomUUID();
    b.customerName = "Client";
    b.customerPhone = "0820000000";
    b.customerEmail = "client@test.local";
    b.startAt = paidAt.plus(1, ChronoUnit.DAYS);
    b.endAt = b.startAt.plus(1, ChronoUnit.HOURS);
    b.status = status;
    b.amountZar = BigDecimal.valueOf(grossCents, 2);
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    b.createdAt = paidAt;
    bookings.save(b);
    return attempt(OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, tenantId, grossCents, feeCents, outcome, paidAt);
  }

  OnlinePaymentAttemptEntity bookingPayment(UUID tenantId, long grossCents, Long feeCents) {
    return bookingPayment(
        tenantId, grossCents, feeCents, SalonBookingEntity.Status.confirmed, OnlinePaymentAttemptEntity.Outcome.PAID,
        Instant.now().minusSeconds(60));
  }

  /** A settled in-app order payment for an order in {@code status}. */
  OnlinePaymentAttemptEntity orderPayment(
      UUID tenantId, long grossCents, Long feeCents, OrderEntity.OrderStatus status,
      OnlinePaymentAttemptEntity.Outcome outcome, Instant paidAt) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.createdAt = paidAt;
    o.customerName = "Client";
    o.customerEmail = "client@test.local";
    o.deliveryType = OrderEntity.DeliveryType.values()[0];
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.online;
    o.status = status;
    o.subtotalZar = BigDecimal.valueOf(grossCents, 2);
    o.totalZar = BigDecimal.valueOf(grossCents, 2);
    orders.save(o);
    return attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, tenantId, grossCents, feeCents, outcome, paidAt);
  }

  private OnlinePaymentAttemptEntity attempt(
      OnlinePaymentAttemptEntity.Kind kind, UUID recordId, UUID tenantId, long grossCents, Long feeCents,
      OnlinePaymentAttemptEntity.Outcome outcome, Instant paidAt) {
    OnlinePaymentAttemptEntity a = new OnlinePaymentAttemptEntity();
    a.id = UUID.randomUUID();
    a.kind = kind;
    a.recordId = recordId;
    a.tenantId = tenantId;
    a.reference = (kind == OnlinePaymentAttemptEntity.Kind.ORDER ? "ORD" : "BKG")
        + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    a.amountCents = grossCents;
    a.startedAt = paidAt.minusSeconds(30);
    a.outcome = outcome;
    a.outcomeAt = outcome == OnlinePaymentAttemptEntity.Outcome.PENDING ? null : paidAt;
    a.feeCents = feeCents;
    return attempts.save(a);
  }

  User user(Role role, UUID tenantId) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = role.name().toLowerCase() + "-" + u.id.toString().substring(0, 8) + "@payout.test";
    u.passwordHash = "unused";
    u.createdAt = Instant.now();
    users.save(u);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = tenantId;
    m.role = role;
    m.createdAt = Instant.now();
    memberships.save(m);
    String token = jwt.mintToken(u.id, u.email, List.of(role), tenantId, null);
    return new User(u.id, u.email, token, new ApiUserPrincipal(u.id, u.email, List.of(role), null));
  }
}
