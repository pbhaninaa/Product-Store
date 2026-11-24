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

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
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

  /**
   * A payment confirmed by the provider. {@code amountCents} is what was asked for (cents);
   * {@code chargeCoversRequest} is false when the provider charged less than that. {@code kind}/{@code
   * recordId} come from our metadata. {@code feeCents} is the provider fee the merchant bears (cents), or
   * null when the provider did not say.
   */
  public record ConfirmedPayment(
      String reference,
      String providerPaymentId,
      long amountCents,
      boolean chargeCoversRequest,
      String currency,
      OnlinePaymentChannel channel,
      String kind,
      String recordId,
      Long feeCents) {

    /** A confirmation without a known fee. */
    public ConfirmedPayment(
        String reference,
        String providerPaymentId,
        long amountCents,
        boolean chargeCoversRequest,
        String currency,
        OnlinePaymentChannel channel,
        String kind,
        String recordId) {
      this(reference, providerPaymentId, amountCents, chargeCoversRequest, currency, channel, kind, recordId, null);
    }

    public ConfirmedPayment(
        String reference,
        String providerPaymentId,
        long amountCents,
        String currency,
        OnlinePaymentChannel channel,
        String kind,
        String recordId) {
      this(reference, providerPaymentId, amountCents, true, currency, channel, kind, recordId);
    }
  }

  /**
   * An attempt was paid but marking its record paid failed unexpectedly, after the checks that should have
   * caught it. Thrown out of {@link #settle} so its transaction rolls back (releasing the row locks); the
   * caller then records the refund with {@link #recordNeedsRefund} in a new transaction.
   */
  public static class AttemptCannotBeAppliedException extends RuntimeException {
    private final UUID attemptId;
    private final String reference;

    public AttemptCannotBeAppliedException(UUID attemptId, String reference, String why, Throwable cause) {
      super(why, cause);
      this.attemptId = attemptId;
      this.reference = reference;
    }

    public UUID attemptId() {
      return attemptId;
    }

    public String reference() {
      return reference;
    }
  }

  public enum Outcome {
    SETTLED,
    ALREADY_SETTLED,
    NOT_FOUND,
    /** Paid, but the record was already paid by another payment; recorded on the attempt and logged once. */
    DUPLICATE,
    /** Paid, but it cannot be applied; recorded on the attempt and logged once. */
    NEEDS_REFUND,
    /**
     * Paid (a booking fee), but its booking was cancelled meanwhile: the payment is refunded automatically, or was
     * already being refunded (a repeated notification).
     */
    REFUNDING
  }

  private final OnlinePaymentAttemptRepository attempts;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final CheckoutService checkoutService;
  private final SalonBookingService salonBookingService;
  private final MerchantSubscriptionService merchantSubscriptionService;
  private final BookingFeeService bookingFees;

  public OnlinePaymentSettlementService(
      OnlinePaymentAttemptRepository attempts,
      OrderRepository orders,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      MerchantSubscriptionRepository merchantSubscriptions,
      CheckoutService checkoutService,
      SalonBookingService salonBookingService,
      MerchantSubscriptionService merchantSubscriptionService,
      BookingFeeService bookingFees) {
    this.bookingFees = bookingFees;
    this.attempts = attempts;
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
   * Order and booking payments are found through their attempt in the ledger (any attempt, not only the
   * latest), and the attempt records the outcome. Without an attempt (subscription payments, and attempts
   * started before the ledger existed) the record is found by our payment reference (looking first in the
   * table its prefix, or else the metadata kind, points to), falling back to the {@code kind} and id in the
   * checkout metadata. Amount and currency are always checked before anything is marked paid.
   */
  @Transactional
  public Outcome settle(ConfirmedPayment payment) {
    String reference = trimToNull(payment.reference());
    String metadataKind = payment.kind() == null ? null : payment.kind().trim().toLowerCase(Locale.ROOT);
    String prefixKind = kindOfReference(reference);
    if (KIND_ORDER.equals(prefixKind) || KIND_BOOKING.equals(prefixKind)) {
      // Plain lookup first; only an existing attempt is locked (by id), so unknown references take no gap lock.
      Optional<OnlinePaymentAttemptEntity> attempt =
          attempts.findByReference(reference).flatMap(found -> attempts.lockById(found.id));
      if (attempt.isPresent()) {
        return settleAttempt(attempt.get(), payment);
      }
    }
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

  /**
   * Records an attempt as needing a refund after {@link AttemptCannotBeAppliedException}. Call it outside the
   * failed transaction (that one is rolled back); it runs in its own. Logs the marker only on the change.
   */
  @Transactional
  public Outcome recordNeedsRefund(UUID attemptId, String reference, String why) {
    OnlinePaymentAttemptEntity a = attempts.lockById(attemptId).orElse(null);
    if (a == null) {
      log.error("{}: reference {} was paid but {}", NEEDS_REFUND, reference, why);
      return Outcome.NEEDS_REFUND;
    }
    return switch (a.outcome) {
      case PAID -> Outcome.ALREADY_SETTLED;
      case DUPLICATE -> Outcome.DUPLICATE;
      case REFUND_PENDING, REFUNDED -> Outcome.REFUNDING;
      default -> needsRefund(a, new ConfirmedPayment(reference, null, 0, null, null, null, null), why);
    };
  }

  // ------------------------------------------------------------------ attempts (orders, bookings)

  /**
   * Applies a confirmed payment of one attempt. Never throws for "paid but cannot apply" cases: those are
   * recorded on the attempt (so they are logged once and never processed again) and reported as an outcome.
   */
  private Outcome settleAttempt(OnlinePaymentAttemptEntity a, ConfirmedPayment payment) {
    switch (a.outcome) {
      case PAID -> {
        return Outcome.ALREADY_SETTLED;
      }
      case DUPLICATE -> {
        return Outcome.DUPLICATE;
      }
      case NEEDS_REFUND -> {
        return Outcome.NEEDS_REFUND;
      }
      case REFUND_PENDING, REFUNDED -> {
        // A booking fee already being refunded (a repeated notification): never applied or refunded twice.
        return Outcome.REFUNDING;
      }
      default -> {
        // PENDING, or NOT_PAID that was paid after its window closed: apply it now.
      }
    }
    if (a.kind == OnlinePaymentAttemptEntity.Kind.BOOKING) {
      SalonBookingEntity b = bookings.lockById(a.recordId).orElse(null);
      if (b == null) return needsRefund(a, payment, "booking " + RefCodes.shortRef(a.recordId) + " no longer exists");
      return settleBookingAttempt(a, b, payment);
    }
    OrderEntity o = orders.lockById(a.recordId).orElse(null);
    if (o == null) return needsRefund(a, payment, "order " + RefCodes.shortRef(a.recordId) + " no longer exists");
    return settleOrderAttempt(a, o, payment);
  }

  private Outcome settleOrderAttempt(OnlinePaymentAttemptEntity a, OrderEntity o, ConfirmedPayment payment) {
    String ref = "order " + RefCodes.shortRef(o.id);
    if (!OnlinePaymentMethods.isOnline(o.paymentMethod)) {
      return needsRefund(a, payment, ref + " is not an online order");
    }
    if (!amountAndCurrencyMatch(payment, o.totalZar)) {
      return needsRefund(a, payment, ref + " was paid a different amount or currency");
    }
    if (o.status == OrderEntity.OrderStatus.paid) {
      if (paidByThisPayment(payment, o.onlinePaymentReference, o.onlinePaymentProviderId)) {
        markPaid(a, payment);
        return Outcome.ALREADY_SETTLED;
      }
      return duplicate(a, payment, ref, o.onlinePaymentReference);
    }
    if (o.status == OrderEntity.OrderStatus.cancelled && o.expiredAt == null) {
      // Cancelled by the merchant or the customer: it is not revived (only expiry is).
      return needsRefund(a, payment, ref + " was cancelled");
    }
    if (!checkoutService.canDeductStock(o)) {
      // A late payment for an expired order whose products sold out (or had stock lowered) meanwhile.
      return needsRefund(a, payment, ref + " cannot be supplied: not enough stock");
    }
    o.onlinePaymentReference = a.reference;
    if (trimToNull(payment.providerPaymentId()) != null) o.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) o.onlinePaymentChannel = payment.channel();
    orders.save(o);
    try {
      checkoutService.finalizeOnlinePaidOrder(o);
    } catch (RuntimeException e) {
      // Safety net: the checks above should prevent this. The transaction is rolled back; the caller records
      // the refund in a new one.
      throw new AttemptCannotBeAppliedException(a.id, a.reference, ref + " could not be marked paid (" + e.getMessage() + ")", e);
    }
    markPaid(a, payment);
    log.info("Online payment settled {} (attempt {})", ref, a.reference);
    return Outcome.SETTLED;
  }

  private Outcome settleBookingAttempt(OnlinePaymentAttemptEntity a, SalonBookingEntity b, ConfirmedPayment payment) {
    if (b.hasBookingFee()) {
      return settleBookingFeeAttempt(a, b, payment);
    }
    String ref = "booking " + RefCodes.shortRef(b.id);
    if (!OnlinePaymentMethods.isOnline(b.clientPaymentMethod)) {
      return needsRefund(a, payment, ref + " is not an online booking");
    }
    SalonServiceEntity svc = salonServices.findByIdAndTenantId(b.serviceId, b.tenantId).orElse(null);
    BigDecimal payable = b.amountZar == null && svc == null ? null : bookingPayable(b, svc);
    if (!amountAndCurrencyMatch(payment, payable)) {
      return needsRefund(a, payment, ref + " was paid a different amount or currency");
    }
    if (b.status == SalonBookingEntity.Status.confirmed
        || b.status == SalonBookingEntity.Status.in_progress
        || b.status == SalonBookingEntity.Status.completed) {
      if (paidByThisPayment(payment, b.onlinePaymentReference, b.onlinePaymentProviderId)) {
        markPaid(a, payment);
        return Outcome.ALREADY_SETTLED;
      }
      return duplicate(a, payment, ref, b.onlinePaymentReference);
    }
    if (b.status != SalonBookingEntity.Status.pending) {
      return needsRefund(a, payment, ref + " is " + b.status);
    }
    b.onlinePaymentReference = a.reference;
    if (trimToNull(payment.providerPaymentId()) != null) b.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) b.onlinePaymentChannel = payment.channel();
    bookings.save(b);
    try {
      salonBookingService.finalizeOnlinePaidBooking(b);
    } catch (RuntimeException e) {
      throw new AttemptCannotBeAppliedException(a.id, a.reference, ref + " could not be confirmed (" + e.getMessage() + ")", e);
    }
    markPaid(a, payment);
    log.info("Online payment settled {} (attempt {})", ref, a.reference);
    return Outcome.SETTLED;
  }

  /**
   * A booking fee payment of a pay-in-store booking (FR-2): the amount must be the fee fixed on the booking. A fee
   * paid while the booking still waits for it marks the fee paid (the booking stays pending, the remainder is due at
   * the shop). A fee paid after the booking was cancelled is refunded automatically (edge case 4). A second payment
   * of a fee already paid is a duplicate, as for any other payment.
   */
  private Outcome settleBookingFeeAttempt(OnlinePaymentAttemptEntity a, SalonBookingEntity b, ConfirmedPayment payment) {
    String ref = "booking fee " + RefCodes.shortRef(b.id);
    if (!amountAndCurrencyMatch(payment, b.bookingFeeZar)) {
      return needsRefund(a, payment, ref + " was paid a different amount or currency");
    }
    if (BookingFeeService.feePaid(b)) {
      if (paidByThisPayment(payment, b.onlinePaymentReference, b.onlinePaymentProviderId)) {
        markPaid(a, payment);
        return Outcome.ALREADY_SETTLED;
      }
      return duplicate(a, payment, ref, b.onlinePaymentReference);
    }
    if (b.status != SalonBookingEntity.Status.pending
        || b.bookingFeeStatus != SalonBookingEntity.BookingFeeStatus.awaiting_payment) {
      if (a.feeCents == null && payment.feeCents() != null && payment.feeCents() >= 0) a.feeCents = payment.feeCents();
      bookingFees.refundPaymentForCancelledBooking(a, b);
      log.warn("Online payment for {} arrived after the booking was {}; refunding it (attempt {})", ref, b.status, a.reference);
      return Outcome.REFUNDING;
    }
    b.onlinePaymentReference = a.reference;
    if (trimToNull(payment.providerPaymentId()) != null) b.onlinePaymentProviderId = payment.providerPaymentId().trim();
    if (payment.channel() != null) b.onlinePaymentChannel = payment.channel();
    bookingFees.markPaid(b, a);
    bookings.save(b);
    markPaid(a, payment);
    log.info("Online payment settled {} (attempt {})", ref, a.reference);
    return Outcome.SETTLED;
  }

  /** True when the record was marked paid by this very payment (same reference and provider transaction). */
  private static boolean paidByThisPayment(ConfirmedPayment payment, String storedReference, String storedProviderId) {
    String paidRef = trimToNull(payment.reference());
    String paidId = trimToNull(payment.providerPaymentId());
    return paidRef != null
        && paidRef.equals(storedReference)
        && storedProviderId != null
        && (paidId == null || paidId.equals(storedProviderId));
  }

  private Outcome needsRefund(OnlinePaymentAttemptEntity a, ConfirmedPayment payment, String why) {
    if (a.outcome != OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND) {
      markOutcome(a, OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND);
      log.error("{}: reference {} was paid but {}", NEEDS_REFUND, payment.reference(), why);
    }
    return Outcome.NEEDS_REFUND;
  }

  private Outcome duplicate(OnlinePaymentAttemptEntity a, ConfirmedPayment payment, String recordRef, String storedRef) {
    if (a.outcome != OnlinePaymentAttemptEntity.Outcome.DUPLICATE) {
      markOutcome(a, OnlinePaymentAttemptEntity.Outcome.DUPLICATE);
      log.error(
          "Possible duplicate online payment: reference {} for {} which is already paid with reference {}",
          payment.reference(),
          recordRef,
          storedRef);
    }
    return Outcome.DUPLICATE;
  }

  /**
   * The attempt became PAID: also records the Paystack fee the merchant bears, once. An attempt becomes PAID
   * only once (settling returns early afterwards), and a fee already stored is never replaced, so a repeated
   * webhook or verify cannot change it. Null stays null (unknown).
   */
  private void markPaid(OnlinePaymentAttemptEntity a, ConfirmedPayment payment) {
    if (a.feeCents == null && payment.feeCents() != null && payment.feeCents() >= 0) {
      a.feeCents = payment.feeCents();
    }
    markOutcome(a, OnlinePaymentAttemptEntity.Outcome.PAID);
  }

  private void markOutcome(OnlinePaymentAttemptEntity a, OnlinePaymentAttemptEntity.Outcome outcome) {
    a.outcome = outcome;
    a.outcomeAt = Instant.now();
    attempts.save(a);
  }

  private static boolean amountAndCurrencyMatch(ConfirmedPayment payment, BigDecimal expectedZar) {
    return payment.chargeCoversRequest()
        && amountAndCurrencyMatch(payment.amountCents(), payment.currency(), expectedZar);
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
    BigDecimal amount = svc == null || svc.priceZar == null ? BigDecimal.ZERO : svc.priceZar;
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
    if (expectedZar == null || !payment.chargeCoversRequest() || payment.amountCents() != toCents(expectedZar)) {
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
