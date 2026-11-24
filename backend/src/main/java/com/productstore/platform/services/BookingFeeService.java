package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.productstore.platform.entities.BookingFeeEventEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonBookingEntity.BookingFeeReason;
import com.productstore.platform.entities.SalonBookingEntity.BookingFeeStatus;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.util.OnlinePaymentMethods;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The booking fee of pay-in-store salon bookings (requirements/booking-fee.md): whether a new booking carries one,
 * recording its payment, and what becomes of it when the booking is cancelled (refunded or kept, BR-4 to BR-6).
 * Every decision is made here, server side (NFR-2); callers hold the booking row lock and the transaction.
 */
@Service
public class BookingFeeService {
  private static final Logger log = LoggerFactory.getLogger(BookingFeeService.class);

  /** The fee of a new booking: fixed on the booking when it is made (BR-7). */
  public record Quote(int percent, BigDecimal feeZar, BigDecimal remainderZar) {}

  /** Who or what cancelled a booking, for the refund rule. */
  public enum CancelledBy {
    CUSTOMER,
    PROVIDER,
    NO_SHOW,
    EXPIRY
  }

  private final OnlinePaymentAvailability onlinePayments;
  private final OnlinePaymentAttemptRepository attempts;
  private final BookingFeeRefundService refunds;
  private final BookingFeeAudit audit;
  private final Clock clock;

  public BookingFeeService(
      OnlinePaymentAvailability onlinePayments,
      OnlinePaymentAttemptRepository attempts,
      BookingFeeRefundService refunds,
      BookingFeeAudit audit,
      ObjectProvider<Clock> clockProvider) {
    this.onlinePayments = onlinePayments;
    this.attempts = attempts;
    this.refunds = refunds;
    this.audit = audit;
    this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
  }

  // ------------------------------------------------------------------ new booking

  /**
   * The fee a new booking carries, or empty when none applies: not pay-in-store, the shop's fee is 0%, online
   * payment is not available on the platform, or the fee is below Paystack's minimum charge (FR-2, FR-3, BR-2,
   * open question 1). An empty quote means the cash booking behaves exactly as before.
   */
  public Optional<Quote> quote(ShopSettingsEntity shop, SalonBookingEntity.ClientPaymentMethod method, BigDecimal amountZar) {
    if (method != SalonBookingEntity.ClientPaymentMethod.cash_store) return Optional.empty();
    int percent = configuredPercent(shop);
    if (percent <= 0) return Optional.empty();
    BigDecimal fee = BookingFeePolicy.feeFor(amountZar, percent);
    if (fee.signum() <= 0 || !onlinePayments.canCharge(OnlinePaymentSettlementService.toCents(fee))) {
      return Optional.empty();
    }
    return Optional.of(new Quote(percent, fee, BookingFeePolicy.remainder(amountZar, fee)));
  }

  /** The shop's configured percent (0 when unset). */
  public static int configuredPercent(ShopSettingsEntity shop) {
    return BookingFeePolicy.effectivePercent(shop == null ? null : shop.bookingFeePercent);
  }

  /** Fixes the quoted fee on a new booking: it waits for the online payment. */
  public static void apply(SalonBookingEntity b, Quote q) {
    b.bookingFeePercent = q.percent();
    b.bookingFeeZar = q.feeZar();
    b.bookingFeeStatus = BookingFeeStatus.awaiting_payment;
  }

  // ------------------------------------------------------------------ payment (settlement)

  /** The fee checkout of attempt {@code a} was paid and applied: the remainder is now due at the shop. */
  public void markPaid(SalonBookingEntity b, OnlinePaymentAttemptEntity a) {
    Instant now = clock.instant();
    b.bookingFeeStatus = BookingFeeStatus.paid;
    b.bookingFeePaidAt = now;
    audit.record(b, BookingFeeEventEntity.Type.FEE_PAID, null, a.amountCents, a.reference, null, now);
  }

  /**
   * Edge case 4: the fee was paid after its booking was cancelled (the 2 hour window or any other cancellation).
   * The booking stays cancelled and the payment is refunded automatically.
   */
  public void refundPaymentForCancelledBooking(OnlinePaymentAttemptEntity a, SalonBookingEntity b) {
    refunds.request(a, b, BookingFeeReason.payment_after_cancellation);
  }

  // ------------------------------------------------------------------ cancellation

  /**
   * Decides the fee when {@code b} has just been cancelled at {@code now} (BR-4 to BR-6): an unpaid fee is closed;
   * a paid fee is refunded (customer at least 24 hours ahead, or the provider at any time) or kept (customer later,
   * or a no-show). A fee already refunded or kept is left alone. No-op for bookings without a fee.
   */
  public void onCancelled(SalonBookingEntity b, CancelledBy by, Instant now) {
    if (!b.hasBookingFee()) return;
    if (b.bookingFeeStatus == BookingFeeStatus.awaiting_payment) {
      b.bookingFeeStatus = BookingFeeStatus.not_paid;
      b.bookingFeeOutcomeAt = now;
      return;
    }
    if (b.bookingFeeStatus != BookingFeeStatus.paid) return;
    BookingFeeReason reason = reasonFor(b, by, now);
    if (reason == BookingFeeReason.late_cancellation || reason == BookingFeeReason.no_show) {
      keep(b, reason, now);
      return;
    }
    OnlinePaymentAttemptEntity paid = paidAttempt(b).orElse(null);
    if (paid == null) {
      // Should not happen: the fee is paid through an attempt. Leave it for a person, never lose it silently.
      b.bookingFeeStatus = BookingFeeStatus.refund_pending;
      b.bookingFeeReason = reason;
      b.bookingFeeOutcomeAt = now;
      log.error(
          "{}: booking fee of booking {} must be refunded ({}) but no paid payment was found",
          OnlinePaymentSettlementService.NEEDS_REFUND, RefCodes.shortRef(b.id), reason);
      return;
    }
    refunds.request(paid, b, reason);
  }

  static BookingFeeReason reasonFor(SalonBookingEntity b, CancelledBy by, Instant now) {
    return switch (by) {
      case CUSTOMER ->
          BookingFeePolicy.customerCancellationRefunds(b.startAt, now)
              ? BookingFeeReason.customer_cancellation
              : BookingFeeReason.late_cancellation;
      case NO_SHOW -> BookingFeeReason.no_show;
      case PROVIDER, EXPIRY -> BookingFeeReason.provider_cancellation;
    };
  }

  private void keep(SalonBookingEntity b, BookingFeeReason reason, Instant now) {
    b.bookingFeeStatus = BookingFeeStatus.kept;
    b.bookingFeeReason = reason;
    b.bookingFeeOutcomeAt = now;
    audit.record(
        b, BookingFeeEventEntity.Type.FEE_KEPT, reason, OnlinePaymentSettlementService.toCents(b.bookingFeeZar),
        b.onlinePaymentReference, null, now);
  }

  /** The PAID attempt that paid the fee (the booking's stored reference first), locked. */
  private Optional<OnlinePaymentAttemptEntity> paidAttempt(SalonBookingEntity b) {
    Optional<OnlinePaymentAttemptEntity> byRef =
        b.onlinePaymentReference == null
            ? Optional.empty()
            : attempts.findByKindAndRecordIdAndReference(
                OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, b.onlinePaymentReference);
    Optional<OnlinePaymentAttemptEntity> found =
        byRef.filter(a -> a.outcome == OnlinePaymentAttemptEntity.Outcome.PAID)
            .or(
                () ->
                    attempts
                        .findByKindAndRecordIdAndOutcome(
                            OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, OnlinePaymentAttemptEntity.Outcome.PAID)
                        .stream()
                        .findFirst());
    return found.flatMap(a -> attempts.lockById(a.id));
  }

  // ------------------------------------------------------------------ views

  /** True once the customer has paid the fee (whatever became of it afterwards). */
  public static boolean feePaid(SalonBookingEntity b) {
    return b.hasBookingFee()
        && (b.bookingFeeStatus == BookingFeeStatus.paid
            || b.bookingFeeStatus == BookingFeeStatus.kept
            || b.bookingFeeStatus == BookingFeeStatus.refund_pending
            || b.bookingFeeStatus == BookingFeeStatus.refunded);
  }

  /** What the customer still pays at the shop now (zero once fully paid, cancelled, or for in-app bookings). */
  public static BigDecimal amountDueAtShop(SalonBookingEntity b, BigDecimal amountZar) {
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.cash_store
        || b.status != SalonBookingEntity.Status.pending
        || amountZar == null) {
      return BigDecimal.ZERO.setScale(2);
    }
    return b.hasBookingFee() ? BookingFeePolicy.remainder(amountZar, b.bookingFeeZar) : amountZar.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * The booking fee as customers and staff see it, or null for a booking without one: percent, fee, remainder,
   * status, reason, and whether a customer cancelling now would get it back.
   */
  public static Map<String, Object> describe(SalonBookingEntity b, Instant now) {
    if (!b.hasBookingFee()) return null;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("percent", b.bookingFeePercent);
    m.put("feeZar", money(b.bookingFeeZar));
    m.put("remainderZar", money(BookingFeePolicy.remainder(b.amountZar, b.bookingFeeZar)));
    m.put("totalZar", money(b.amountZar));
    m.put("status", b.bookingFeeStatus.name());
    m.put("reason", b.bookingFeeReason == null ? null : b.bookingFeeReason.name());
    m.put("paidAt", b.bookingFeePaidAt == null ? null : b.bookingFeePaidAt.toString());
    m.put("refundableIfCancelledNow", BookingFeePolicy.customerCancellationRefunds(b.startAt, now));
    return m;
  }

  /**
   * Receipt and Payments page lines (FR-7) as {@code {kind, amountZar}}, labelled by the frontend: the booking fee
   * (online), the amount paid or due at the shop (cash), a refunded or kept fee on its own line, and the total.
   */
  public static List<Map<String, Object>> receiptLines(SalonBookingEntity b, BigDecimal amountZar) {
    List<Map<String, Object>> lines = new ArrayList<>();
    if (amountZar == null) return lines;
    boolean fullyPaid =
        b.status == SalonBookingEntity.Status.confirmed
            || b.status == SalonBookingEntity.Status.in_progress
            || b.status == SalonBookingEntity.Status.completed;
    boolean cancelled = b.status == SalonBookingEntity.Status.cancelled;
    if (OnlinePaymentMethods.isOnline(b.clientPaymentMethod)) {
      lines.add(line(fullyPaid ? "paid_online" : "due_online", amountZar));
    } else if (b.hasBookingFee()) {
      if (feePaid(b)) {
        lines.add(line("booking_fee_online", b.bookingFeeZar));
      } else if (b.bookingFeeStatus == BookingFeeStatus.awaiting_payment) {
        lines.add(line("booking_fee_due_online", b.bookingFeeZar));
      }
      BigDecimal remainder = BookingFeePolicy.remainder(amountZar, b.bookingFeeZar);
      if (fullyPaid) {
        lines.add(line("paid_at_shop", remainder));
      } else if (!cancelled) {
        lines.add(line("due_at_shop", remainder));
      }
      switch (b.bookingFeeStatus) {
        case refund_pending -> lines.add(line("fee_refund_in_progress", b.bookingFeeZar));
        case refunded -> lines.add(line("fee_refunded", b.bookingFeeZar));
        case kept -> lines.add(line("fee_kept", b.bookingFeeZar));
        default -> {
          // no extra line
        }
      }
    } else if (fullyPaid) {
      lines.add(line("paid_at_shop", amountZar));
    } else if (!cancelled) {
      lines.add(line("due_at_shop", amountZar));
    }
    lines.add(line("total", amountZar));
    return lines;
  }

  private static Map<String, Object> line(String kind, BigDecimal amount) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("kind", kind);
    m.put("amountZar", money(amount));
    return m;
  }

  private static String money(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
