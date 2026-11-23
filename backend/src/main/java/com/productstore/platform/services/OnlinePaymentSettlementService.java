package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.util.OnlinePaymentMethods;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles a payment the provider has confirmed (signed webhook or verify call) against the order, salon
 * booking or subscription payment it pays for. Provider neutral: the caller has already authenticated the
 * confirmation. The record row is locked, so concurrent deliveries (or several app instances) settle it once.
 *
 * <p>Log markers for people to act on: {@code ONLINE_PAYMENT_NEEDS_REFUND} (money taken for a record that can
 * no longer be paid, or for the wrong amount) and "possible duplicate online payment" (a second payment
 * for a record that is already paid). Logs carry references and short record refs only, never PII.
 */
@Service
public class OnlinePaymentSettlementService {
  public static final String KIND_ORDER = "order";
  public static final String KIND_BOOKING = "booking";
  public static final String KIND_SUBSCRIPTION = "subscription";
  public static final String CURRENCY = "ZAR";

  /** Reference prefixes this app gives each kind of checkout. */
  public static final String PREFIX_ORDER = "ORD";
  public static final String PREFIX_BOOKING = "BKG";
  public static final String PREFIX_SUBSCRIPTION = "SUB";

  static final String NEEDS_REFUND = "ONLINE_PAYMENT_NEEDS_REFUND";

  private static final Logger log = LoggerFactory.getLogger(OnlinePaymentSettlementService.class);

  /** A payment confirmed by the provider. Amount is in cents. {@code kind}/{@code recordId} come from our metadata. */
  public record ConfirmedPayment(
      String reference,
      String providerPaymentId,
      long amountCents,
      String currency,
      OnlinePaymentChannel channel,
      String kind,
      String recordId) {}

  public enum Outcome {
    SETTLED,
    ALREADY_SETTLED,
    NOT_FOUND
  }

  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final CheckoutService checkoutService;
  private final SalonBookingService salonBookingService;
  private final MerchantSubscriptionService merchantSubscriptionService;

  public OnlinePaymentSettlementService(
      OrderRepository orders,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      MerchantSubscriptionRepository merchantSubscriptions,
      CheckoutService checkoutService,
      SalonBookingService salonBookingService,
      MerchantSubscriptionService merchantSubscriptionService) {
    this.orders = orders;
    this.bookings = bookings;
    this.salonServices = salonServices;
    this.subscriptionPayments = subscriptionPayments;
    this.merchantSubscriptions = merchantSubscriptions;
    this.checkoutService = checkoutService;
    this.salonBookingService = salonBookingService;
    this.merchantSubscriptionService = merchantSubscriptionService;
  }

  /** The kind our reference prefix stands for, or null for a reference this app did not create. */
  public static String kindOfReference(String reference) {
    if (reference == null) return null;
    String r = reference.trim();
    if (r.startsWith(PREFIX_ORDER)) return KIND_ORDER;
    if (r.startsWith(PREFIX_BOOKING)) return KIND_BOOKING;
    if (r.startsWith(PREFIX_SUBSCRIPTION)) return KIND_SUBSCRIPTION;
    return null;
  }

  /**
   * Finds the record by our payment reference (looking first in the table its prefix, or else the metadata
   * kind, points to), falling back to the {@code kind} and id we put in the checkout metadata (the customer
   * may have paid an earlier checkout attempt). Amount and currency are always checked before anything is
   * marked paid.
   */
  @Transactional
  public Outcome settle(ConfirmedPayment payment) {
    String reference = trimToNull(payment.reference());
    String metadataKind = payment.kind() == null ? null : payment.kind().trim().toLowerCase(Locale.ROOT);
    if (reference != null) {
      for (String kind : lookupOrder(kindOfReference(reference), metadataKind)) {
        Optional<Outcome> out = settleByReference(kind, reference, payment);
        if (out.isPresent()) return out.get();
      }
    }

    UUID recordId = parseUuid(payment.recordId());
    if (recordId != null && metadataKind != null) {
      switch (metadataKind) {
        case KIND_ORDER -> {
          Optional<OrderEntity> o = orders.lockById(recordId);
          if (o.isPresent()) return settleOrder(o.get(), payment);
        }
        case KIND_BOOKING -> {
          Optional<SalonBookingEntity> b = bookings.lockById(recordId);
          if (b.isPresent()) return settleBooking(b.get(), payment);
        }
        case KIND_SUBSCRIPTION -> {
          Optional<SubscriptionOnlinePaymentEntity> p = subscriptionPayments.lockById(recordId);
          if (p.isPresent()) return settleSubscription(p.get(), payment);
        }
        default -> {
          // Unknown kind: not one of ours.
        }
      }
    }
    return Outcome.NOT_FOUND;
  }

  /** The table the reference prefix (else the metadata kind) points to first, then the others. */
  static List<String> lookupOrder(String prefixKind, String metadataKind) {
    List<String> out = new ArrayList<>(3);
    String first = prefixKind != null ? prefixKind : metadataKind;
    if (first != null && List.of(KIND_ORDER, KIND_BOOKING, KIND_SUBSCRIPTION).contains(first)) {
      out.add(first);
    }
    for (String k : List.of(KIND_ORDER, KIND_BOOKING, KIND_SUBSCRIPTION)) {
      if (!out.contains(k)) out.add(k);
    }
    return out;
  }

  private Optional<Outcome> settleByReference(String kind, String reference, ConfirmedPayment payment) {
    return switch (kind) {
      case KIND_ORDER ->
          orders.lockByOnlinePaymentReference(reference).stream().findFirst().map(o -> settleOrder(o, payment));
      case KIND_BOOKING ->
          bookings.lockByOnlinePaymentReference(reference).stream().findFirst().map(b -> settleBooking(b, payment));
      case KIND_SUBSCRIPTION ->
          subscriptionPayments.lockByOnlinePaymentReference(reference).map(p -> settleSubscription(p, payment));
      default -> Optional.empty();
    };
  }

  private Outcome settleOrder(OrderEntity o, ConfirmedPayment payment) {
    String ref = "order " + RefCodes.shortRef(o.id);
    if (!OnlinePaymentMethods.isOnline(o.paymentMethod)) {
      throw new IllegalArgumentException("not_online_order");
    }
    assertPaid(payment, o.totalZar, ref);
    if (o.status == OrderEntity.OrderStatus.paid) {
      warnIfDuplicate(payment, o.onlinePaymentReference, o.onlinePaymentProviderId, ref);
      return Outcome.ALREADY_SETTLED;
    }
    if (o.status == OrderEntity.OrderStatus.cancelled && o.expiredAt == null) {
      // Cancelled by the merchant or the customer: it is not revived (only expiry is).
      log.error("{}: reference {} paid for {} which was cancelled", NEEDS_REFUND, payment.reference(), ref);
      throw new IllegalArgumentException("order_not_pending");
    }
    applyPaymentDetails(payment, o.onlinePaymentReference, r -> o.onlinePaymentReference = r);
    if (trimToNull(payment.providerPaymentId()) != null) o.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) o.onlinePaymentChannel = payment.channel();
    orders.save(o);
    try {
      checkoutService.finalizeOnlinePaidOrder(o);
    } catch (IllegalArgumentException e) {
      log.error("{}: reference {} paid for {} that could not be marked paid ({})", NEEDS_REFUND, payment.reference(), ref, e.getMessage());
      throw e;
    }
    log.info("Online payment settled {}", ref);
    return Outcome.SETTLED;
  }

  private Outcome settleBooking(SalonBookingEntity b, ConfirmedPayment payment) {
    String ref = "booking " + RefCodes.shortRef(b.id);
    if (!OnlinePaymentMethods.isOnline(b.clientPaymentMethod)) {
      throw new IllegalArgumentException("not_online_booking");
    }
    SalonServiceEntity svc =
        salonServices
            .findByIdAndTenantId(b.serviceId, b.tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    assertPaid(payment, bookingPayable(b, svc), ref);
    if (b.status == SalonBookingEntity.Status.confirmed
        || b.status == SalonBookingEntity.Status.in_progress
        || b.status == SalonBookingEntity.Status.completed) {
      warnIfDuplicate(payment, b.onlinePaymentReference, b.onlinePaymentProviderId, ref);
      return Outcome.ALREADY_SETTLED;
    }
    if (b.status != SalonBookingEntity.Status.pending) {
      log.error("{}: reference {} paid for {} which is {}", NEEDS_REFUND, payment.reference(), ref, b.status);
      throw new IllegalArgumentException("booking_not_pending");
    }
    applyPaymentDetails(payment, b.onlinePaymentReference, r -> b.onlinePaymentReference = r);
    if (trimToNull(payment.providerPaymentId()) != null) b.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) b.onlinePaymentChannel = payment.channel();
    bookings.save(b);
    try {
      salonBookingService.finalizeOnlinePaidBooking(b);
    } catch (IllegalArgumentException e) {
      log.error("{}: reference {} paid for {} that could not be confirmed ({})", NEEDS_REFUND, payment.reference(), ref, e.getMessage());
      throw e;
    }
    log.info("Online payment settled {}", ref);
    return Outcome.SETTLED;
  }

  private Outcome settleSubscription(SubscriptionOnlinePaymentEntity p, ConfirmedPayment payment) {
    String ref = "subscription payment " + RefCodes.shortRef(p.id);
    assertPaid(payment, p.amount, ref);
    if (SubscriptionOnlinePaymentEntity.STATUS_COMPLETED.equalsIgnoreCase(p.status)) {
      warnIfDuplicate(payment, p.onlinePaymentReference, p.onlinePaymentProviderId, ref);
      return Outcome.ALREADY_SETTLED;
    }
    if (!SubscriptionOnlinePaymentEntity.STATUS_PENDING.equalsIgnoreCase(p.status)) {
      log.error("{}: reference {} paid for {} which is {}", NEEDS_REFUND, payment.reference(), ref, p.status);
      throw new IllegalArgumentException("subscription_payment_not_pending");
    }
    if (trimToNull(payment.providerPaymentId()) != null) p.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) p.onlinePaymentChannel = payment.channel();
    p.status = SubscriptionOnlinePaymentEntity.STATUS_COMPLETED;
    p.completedAt = Instant.now();
    subscriptionPayments.save(p);

    MerchantSubscriptionEntity sub = merchantSubscriptions.findByTenantId(p.tenantId).orElse(null);
    if (sub != null) {
      sub.onlinePaymentProviderId = p.onlinePaymentProviderId;
      sub.onlinePaymentReference = p.onlinePaymentReference;
      sub.onlinePaymentChannel = p.onlinePaymentChannel;
      merchantSubscriptions.save(sub);
    }
    merchantSubscriptionService.finalizeOnlinePaidSubscription(p.tenantId, p.planTier);
    log.info("Online payment settled {}", ref);
    return Outcome.SETTLED;
  }

  /** A second, different payment for a record that is already paid: someone must check and refund one. */
  private static void warnIfDuplicate(
      ConfirmedPayment payment, String storedReference, String storedProviderId, String recordRef) {
    String paidRef = trimToNull(payment.reference());
    String paidId = trimToNull(payment.providerPaymentId());
    boolean otherReference = paidRef != null && storedReference != null && !paidRef.equals(storedReference);
    boolean otherProviderId = paidId != null && storedProviderId != null && !paidId.equals(storedProviderId);
    if (otherReference || otherProviderId) {
      log.error(
          "Possible duplicate online payment: reference {} for {} which is already paid with reference {}",
          paidRef,
          recordRef,
          storedReference);
    }
  }

  /** What the customer owes for a booking: the price fixed at booking, else service price less discount. */
  static BigDecimal bookingPayable(SalonBookingEntity b, SalonServiceEntity svc) {
    if (b.amountZar != null) {
      return b.amountZar.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal amount = svc.priceZar == null ? BigDecimal.ZERO : svc.priceZar;
    if (b.discountZar != null && b.discountZar.compareTo(BigDecimal.ZERO) > 0) {
      amount = amount.subtract(b.discountZar).max(BigDecimal.ZERO);
    }
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  /** Rand amount to cents; fails on fractions of a cent rather than rounding silently. */
  static long toCents(BigDecimal zar) {
    BigDecimal value = zar == null ? BigDecimal.ZERO : zar;
    return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
  }

  /** True when the confirmed amount (cents) and currency are exactly what the record asks for. */
  static boolean amountAndCurrencyMatch(long amountCents, String currency, BigDecimal expectedZar) {
    return currency != null
        && CURRENCY.equalsIgnoreCase(currency.trim())
        && expectedZar != null
        && amountCents == toCents(expectedZar);
  }

  private static void assertPaid(ConfirmedPayment payment, BigDecimal expectedZar, String recordRef) {
    if (payment.currency() == null || !CURRENCY.equalsIgnoreCase(payment.currency().trim())) {
      log.error("{}: reference {} for {} was paid in another currency", NEEDS_REFUND, payment.reference(), recordRef);
      throw new IllegalArgumentException("online_payment_currency_mismatch");
    }
    if (expectedZar == null || payment.amountCents() != toCents(expectedZar)) {
      log.error("{}: reference {} for {} was paid with a different amount", NEEDS_REFUND, payment.reference(), recordRef);
      throw new IllegalArgumentException("online_payment_amount_mismatch");
    }
  }

  /** When the payment arrived through the metadata fallback, record the reference that was actually paid. */
  private static void applyPaymentDetails(
      ConfirmedPayment payment, String currentReference, Consumer<String> setReference) {
    String paidReference = trimToNull(payment.reference());
    if (paidReference != null && !paidReference.equals(currentReference)) {
      setReference.accept(paidReference);
    }
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
