package com.productstore.platform.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.BookingFeeEventEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentRefundEntity;
import com.productstore.platform.entities.ProviderPayoutLineEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OnlinePaymentRefundRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refunds of booking fees through Paystack's Refund API (FR-8, edge cases 4 and 5, NFR-4).
 *
 * <p>A refund is first recorded (one {@code online_payment_refunds} row per payment attempt, unique, so the app can
 * never refund a payment twice), then sent to Paystack after that transaction commits, outside any transaction.
 * Sending first claims the row (a lease on {@code next_try_at}) so two instances never send it at once. A failure
 * keeps the refund {@code PENDING}, flags it for support (log marker {@value #REFUND_FAILED}) and schedules a retry
 * with a backoff; before every retry the transaction is checked with Paystack and a refund that already exists is
 * never sent again. Paystack's {@code refund.processed} / {@code refund.failed} webhooks move it on; replays are
 * no-ops.
 *
 * <p>A fee that was already released to the provider in a payout is not refunded automatically: the platform no
 * longer holds that money. The attempt becomes {@code NEEDS_REFUND} (the existing "a person must refund" state), the
 * refund is {@code MANUAL} and flagged, and the payout is not altered (provider payouts edge case 6).
 */
@Service
public class BookingFeeRefundService {
  static final String REFUND_FAILED = "BOOKING_FEE_REFUND_FAILED";
  static final Duration FIRST_RETRY = Duration.ofMinutes(5);
  static final Duration MAX_RETRY = Duration.ofHours(6);
  /** A claimed refund is not picked up again for this long (covers an instance dying mid-call). */
  static final Duration CLAIM_LEASE = Duration.ofMinutes(10);

  private static final Logger log = LoggerFactory.getLogger(BookingFeeRefundService.class);

  /** What happened when a refund was sent. */
  enum SendResult {
    /** Not due, already sent, or claimed by someone else: nothing was sent now. */
    NOT_DUE,
    ACCEPTED,
    ALREADY_REFUNDED,
    FAILED
  }

  private record Claim(UUID refundId, String reference, long amountCents, String reason, boolean checkFirst) {}

  private final OnlinePaymentRefundRepository refunds;
  private final OnlinePaymentAttemptRepository attempts;
  private final SalonBookingRepository bookings;
  private final ProviderPayoutLineRepository payoutLines;
  private final BookingFeeAudit audit;
  private final PaystackClient paystack;
  private final TransactionTemplate newTx;
  private final Clock clock;

  public BookingFeeRefundService(
      OnlinePaymentRefundRepository refunds,
      OnlinePaymentAttemptRepository attempts,
      SalonBookingRepository bookings,
      ProviderPayoutLineRepository payoutLines,
      BookingFeeAudit audit,
      PaystackClient paystack,
      PlatformTransactionManager transactionManager,
      ObjectProvider<Clock> clockProvider) {
    this.refunds = refunds;
    this.attempts = attempts;
    this.bookings = bookings;
    this.payoutLines = payoutLines;
    this.audit = audit;
    this.paystack = paystack;
    this.newTx = new TransactionTemplate(transactionManager);
    this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
  }

  // ------------------------------------------------------------------ request

  /**
   * Records that the fee paid by attempt {@code a} must be refunded and sends it after the caller's transaction
   * commits. Call inside a transaction that holds the booking (and ideally the attempt) row lock. Idempotent: a
   * second call for the same attempt returns the existing refund.
   *
   * @return the booking fee status after the request ({@code refund_pending})
   */
  public OnlinePaymentRefundEntity request(
      OnlinePaymentAttemptEntity a, SalonBookingEntity b, SalonBookingEntity.BookingFeeReason reason) {
    Optional<OnlinePaymentRefundEntity> existing = refunds.findByPaymentAttemptId(a.id);
    if (existing.isPresent()) return existing.get();
    Instant now = clock.instant();
    OnlinePaymentRefundEntity r = new OnlinePaymentRefundEntity();
    r.id = UUID.randomUUID();
    r.paymentAttemptId = a.id;
    r.tenantId = a.tenantId;
    r.recordId = a.recordId;
    r.paymentReference = a.reference;
    r.amountCents = a.amountCents;
    r.reason = reason;
    r.requestedAt = now;
    b.bookingFeeStatus = SalonBookingEntity.BookingFeeStatus.refund_pending;
    b.bookingFeeReason = reason;
    b.bookingFeeOutcomeAt = now;
    bookings.save(b);
    audit.record(b, BookingFeeEventEntity.Type.REFUND_REQUESTED, reason, a.amountCents, a.reference, null, now);

    Optional<ProviderPayoutLineEntity> released = payoutLines.findFirstByPaymentAttemptId(a.id);
    if (released.isPresent()) {
      // Already paid out to the provider: a person refunds and recovers it (existing NEEDS_REFUND handling).
      r.status = OnlinePaymentRefundEntity.Status.MANUAL;
      r.flaggedForSupport = true;
      r.nextTryAt = null;
      refunds.save(r);
      setOutcome(a, OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND, now);
      String detail = "already paid out in payout " + released.get().payoutId;
      audit.record(b, BookingFeeEventEntity.Type.REFUND_FLAGGED, reason, a.amountCents, a.reference, detail, now);
      log.error(
          "{}: booking fee reference {} for booking {} must be refunded ({}) but was {}; a person must refund it",
          OnlinePaymentSettlementService.NEEDS_REFUND, a.reference, RefCodes.shortRef(b.id), reason, detail);
      return r;
    }
    r.status = OnlinePaymentRefundEntity.Status.PENDING;
    r.nextTryAt = now;
    refunds.save(r);
    setOutcome(a, OnlinePaymentAttemptEntity.Outcome.REFUND_PENDING, now);
    sendAfterCommit(r.id);
    return r;
  }

  private void sendAfterCommit(UUID refundId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send(refundId);
            }
          });
    } else {
      send(refundId);
    }
  }

  // ------------------------------------------------------------------ send

  /** Sends one refund to Paystack if it is due and not claimed by someone else. Never throws. */
  public SendResult send(UUID refundId) {
    Claim claim;
    try {
      claim = newTx.execute(status -> claim(refundId));
    } catch (RuntimeException e) {
      log.warn("Booking fee refund {} could not be claimed: {}", refundId, e.toString());
      return SendResult.FAILED;
    }
    if (claim == null) return SendResult.NOT_DUE;
    SendResult result;
    String providerRefundId = null;
    String error = null;
    try {
      if (claim.checkFirst() && alreadyReversed(claim.reference())) {
        result = SendResult.ALREADY_REFUNDED;
      } else {
        PaystackClient.RefundResult r =
            paystack.refund(
                new PaystackClient.RefundRequest(
                    claim.reference(),
                    claim.amountCents(),
                    OnlinePaymentSettlementService.CURRENCY,
                    "Booking fee refund (" + claim.reason() + ")"));
        result = r.alreadyRefunded() ? SendResult.ALREADY_REFUNDED : SendResult.ACCEPTED;
        providerRefundId = r.refundId();
      }
    } catch (PaystackClient.PaystackException e) {
      result = SendResult.FAILED;
      error = e.getMessage();
    } catch (RuntimeException e) {
      result = SendResult.FAILED;
      error = e.getClass().getSimpleName();
    }
    SendResult finalResult = result;
    String finalProviderId = providerRefundId;
    String finalError = error;
    try {
      newTx.executeWithoutResult(status -> recordSend(refundId, finalResult, finalProviderId, finalError));
    } catch (RuntimeException e) {
      // The claim lease expires and the retry checks Paystack first, so nothing is refunded twice.
      log.warn("Booking fee refund {} result could not be recorded: {}", refundId, e.toString());
    }
    return result;
  }

  /** Locks the refund; when it is due, counts the try and leases it. Null when there is nothing to send now. */
  private Claim claim(UUID refundId) {
    OnlinePaymentRefundEntity r = refunds.lockById(refundId).orElse(null);
    Instant now = clock.instant();
    if (r == null || r.status != OnlinePaymentRefundEntity.Status.PENDING) return null;
    if (r.nextTryAt == null || r.nextTryAt.isAfter(now)) return null;
    r.tries += 1;
    r.nextTryAt = now.plus(CLAIM_LEASE);
    refunds.save(r);
    return new Claim(r.id, r.paymentReference, r.amountCents, String.valueOf(r.reason), r.tries > 1);
  }

  /** Before a retry: a transaction Paystack already shows as reversed has its refund. */
  private boolean alreadyReversed(String reference) {
    return paystack.verify(reference).map(PaystackClient.Transaction::isReversed).orElse(false);
  }

  private void recordSend(UUID refundId, SendResult result, String providerRefundId, String error) {
    OnlinePaymentRefundEntity r = refunds.lockById(refundId).orElse(null);
    if (r == null) return;
    Instant now = clock.instant();
    if (r.status != OnlinePaymentRefundEntity.Status.PENDING) {
      // A webhook got there first; keep what it recorded.
      if (providerRefundId != null && r.providerRefundId == null) r.providerRefundId = providerRefundId;
      refunds.save(r);
      return;
    }
    SalonBookingEntity b = bookings.lockById(r.recordId).orElse(null);
    if (result == SendResult.FAILED) {
      r.lastError = truncate(error);
      r.flaggedForSupport = true;
      r.nextTryAt = now.plus(backoff(r.tries));
      refunds.save(r);
      if (b != null) {
        audit.record(b, BookingFeeEventEntity.Type.REFUND_FAILED, r.reason, r.amountCents, r.paymentReference, r.lastError, now);
      }
      log.error(
          "{}: refund of booking fee reference {} failed (try {}), retrying at {}: {}",
          REFUND_FAILED, r.paymentReference, r.tries, r.nextTryAt, r.lastError);
      return;
    }
    r.status = OnlinePaymentRefundEntity.Status.SUBMITTED;
    r.submittedAt = now;
    r.nextTryAt = null;
    r.lastError = null;
    if (providerRefundId != null) r.providerRefundId = providerRefundId;
    refunds.save(r);
    attempts.lockById(r.paymentAttemptId).ifPresent(a -> setOutcome(a, OnlinePaymentAttemptEntity.Outcome.REFUNDED, now));
    if (b != null) {
      markBookingRefunded(b, now);
      String detail = result == SendResult.ALREADY_REFUNDED ? "Paystack already shows it reversed" : null;
      audit.record(b, BookingFeeEventEntity.Type.REFUND_SUBMITTED, r.reason, r.amountCents, r.paymentReference, detail, now);
    }
  }

  /** {@code min(5 minutes x 2^(tries - 1), 6 hours)}. */
  static Duration backoff(int tries) {
    int exponent = Math.max(0, Math.min(tries - 1, 12));
    Duration d = FIRST_RETRY.multipliedBy(1L << exponent);
    return d.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : d;
  }

  // ------------------------------------------------------------------ retry job

  /** Sends refunds whose retry is due; returns how many were sent (accepted or already refunded). */
  public int sendDue(int limit) {
    List<UUID> due = refunds.findDueIds(clock.instant(), PageRequest.of(0, Math.max(1, limit)));
    int done = 0;
    for (UUID id : due) {
      SendResult r = send(id);
      if (r == SendResult.ACCEPTED || r == SendResult.ALREADY_REFUNDED) done++;
    }
    return done;
  }

  // ------------------------------------------------------------------ webhooks

  /**
   * Handles a signed Paystack refund webhook for a transaction reference. {@code processed} completes the refund;
   * {@code failed} puts it back to {@code PENDING}, flagged, for a retry. Any other status, an unknown reference or
   * a refund we do not track is ignored. Repeated notifications change nothing.
   *
   * @return true when the notification changed something
   */
  public boolean onRefundNotification(String transactionReference, String refundStatus) {
    if (transactionReference == null || refundStatus == null) return false;
    String status = refundStatus.trim().toLowerCase(java.util.Locale.ROOT);
    if (!status.equals("processed") && !status.equals("failed")) return false;
    Optional<OnlinePaymentAttemptEntity> attempt = attempts.findByReference(transactionReference.trim());
    if (attempt.isEmpty()) return false;
    Optional<OnlinePaymentRefundEntity> refund = refunds.findByPaymentAttemptId(attempt.get().id);
    if (refund.isEmpty()) {
      log.info("Paystack refund {} for reference {} is not a booking fee refund; ignored", status, transactionReference);
      return false;
    }
    UUID refundId = refund.get().id;
    Boolean changed =
        newTx.execute(tx -> status.equals("processed") ? markProcessed(refundId) : markFailed(refundId));
    return Boolean.TRUE.equals(changed);
  }

  private boolean markProcessed(UUID refundId) {
    OnlinePaymentRefundEntity r = refunds.lockById(refundId).orElse(null);
    if (r == null || r.status == OnlinePaymentRefundEntity.Status.PROCESSED) return false;
    Instant now = clock.instant();
    r.status = OnlinePaymentRefundEntity.Status.PROCESSED;
    r.processedAt = now;
    r.nextTryAt = null;
    if (r.submittedAt == null) r.submittedAt = now;
    refunds.save(r);
    attempts.lockById(r.paymentAttemptId).ifPresent(a -> setOutcome(a, OnlinePaymentAttemptEntity.Outcome.REFUNDED, now));
    SalonBookingEntity b = bookings.lockById(r.recordId).orElse(null);
    if (b != null) {
      markBookingRefunded(b, now);
      audit.record(b, BookingFeeEventEntity.Type.REFUND_PROCESSED, r.reason, r.amountCents, r.paymentReference, null, now);
    }
    return true;
  }

  private boolean markFailed(UUID refundId) {
    OnlinePaymentRefundEntity r = refunds.lockById(refundId).orElse(null);
    if (r == null || r.status != OnlinePaymentRefundEntity.Status.SUBMITTED) return false;
    Instant now = clock.instant();
    r.status = OnlinePaymentRefundEntity.Status.PENDING;
    r.flaggedForSupport = true;
    r.lastError = "Paystack reported the refund failed";
    r.nextTryAt = now;
    refunds.save(r);
    attempts.lockById(r.paymentAttemptId)
        .ifPresent(a -> setOutcome(a, OnlinePaymentAttemptEntity.Outcome.REFUND_PENDING, now));
    SalonBookingEntity b = bookings.lockById(r.recordId).orElse(null);
    if (b != null) {
      b.bookingFeeStatus = SalonBookingEntity.BookingFeeStatus.refund_pending;
      bookings.save(b);
      audit.record(b, BookingFeeEventEntity.Type.REFUND_FAILED, r.reason, r.amountCents, r.paymentReference, r.lastError, now);
    }
    log.error("{}: Paystack reported the refund of booking fee reference {} failed; retrying", REFUND_FAILED, r.paymentReference);
    return true;
  }

  // ------------------------------------------------------------------ helpers

  private void markBookingRefunded(SalonBookingEntity b, Instant now) {
    if (b.bookingFeeStatus == SalonBookingEntity.BookingFeeStatus.refunded) return;
    b.bookingFeeStatus = SalonBookingEntity.BookingFeeStatus.refunded;
    b.bookingFeeOutcomeAt = now;
    bookings.save(b);
  }

  private void setOutcome(OnlinePaymentAttemptEntity a, OnlinePaymentAttemptEntity.Outcome outcome, Instant now) {
    if (a.outcome == outcome) return;
    a.outcome = outcome;
    a.outcomeAt = now;
    attempts.save(a);
  }

  private static String truncate(String s) {
    if (s == null) return null;
    return s.length() > 255 ? s.substring(0, 255) : s;
  }
}
