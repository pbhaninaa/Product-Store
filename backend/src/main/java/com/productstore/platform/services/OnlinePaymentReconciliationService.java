package com.productstore.platform.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.services.PaystackPaymentService.VerifyResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Confirms online payments without any webhook: the Paystack account is shared with another app and its
 * single webhook URL may point elsewhere.
 *
 * <p>Works off the payment attempt ledger (orders and bookings) plus pending subscription payments. An
 * attempt is checked while it is {@code PENDING} and started within {@link #WINDOW}, whatever became of its
 * record, so a late payment for a cancelled record is recorded as needing a refund (or revives an order
 * cancelled by expiry). Attempts are checked least recently checked first, with a backoff: the next check is
 * due {@code min(2 minutes x 2^checks, 30 minutes)} after the last one. When the window ends, still pending
 * attempts become {@code NOT_PAID}.
 *
 * <p>Every check goes through the same verify cache and idempotent settle path as the return page, and only
 * this app's own references with this record's metadata are ever settled.
 */
@Service
public class OnlinePaymentReconciliationService {
  static final Duration WINDOW = Duration.ofHours(24);
  static final Duration FIRST_BACKOFF = Duration.ofMinutes(2);
  static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
  /** How many pending attempts are read per run to pick the due ones from. */
  static final int SCAN_LIMIT = 500;

  private static final Logger log = LoggerFactory.getLogger(OnlinePaymentReconciliationService.class);

  /** Counts for one run; logged as a single line. */
  public record Summary(
      int checked,
      int settled,
      int alreadySettled,
      int notPaid,
      int pending,
      int rejected,
      int duplicate,
      int needsRefund,
      int errors,
      int closed,
      /** Booking fees paid after their booking was cancelled, now being refunded automatically. */
      int refunding) {

    public Summary(
        int checked, int settled, int alreadySettled, int notPaid, int pending, int rejected, int duplicate,
        int needsRefund, int errors, int closed) {
      this(checked, settled, alreadySettled, notPaid, pending, rejected, duplicate, needsRefund, errors, closed, 0);
    }
  }

  /** {@code attemptId} is set for ledger attempts; subscription payments are checked by {@code recordId}. */
  private record Candidate(
      String kind, UUID recordId, String reference, UUID attemptId, Instant lastCheckedAt, Instant startedAt) {}

  private final OnlinePaymentAttemptRepository attempts;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final PaystackPaymentService paystack;

  public OnlinePaymentReconciliationService(
      OnlinePaymentAttemptRepository attempts,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      PaystackPaymentService paystack) {
    this.attempts = attempts;
    this.subscriptionPayments = subscriptionPayments;
    this.paystack = paystack;
  }

  /**
   * Checks at most {@code limit} due payments. A failure on one candidate is counted and the run goes on;
   * the run stops early only when Paystack itself cannot be reached (the next run tries again).
   */
  public Summary reconcile(Instant now, int limit) {
    Instant since = now.minus(WINDOW);
    int closed = attempts.closeExpired(since, now);
    int checked = 0, settled = 0, already = 0, notPaid = 0, pending = 0, rejected = 0;
    int duplicate = 0, needsRefund = 0, errors = 0, refunding = 0;
    for (Candidate c : candidates(now, since, Math.max(0, limit))) {
      checked++;
      VerifyResult r = null;
      try {
        r = paystack.verifyAndSettle(c.reference(), c.kind(), c.recordId());
      } catch (RuntimeException e) {
        errors++;
        log.warn("Payment reconciliation failed for reference {}: {}", c.reference(), e.toString());
      } finally {
        markChecked(c, now);
      }
      if (r == null) continue;
      switch (r) {
        case SETTLED -> settled++;
        case ALREADY_SETTLED -> already++;
        case NOT_PAID -> notPaid++;
        case REJECTED -> rejected++;
        case DUPLICATE -> duplicate++;
        case NEEDS_REFUND -> needsRefund++;
        case REFUNDING -> refunding++;
        case ERROR -> errors++;
        default -> pending++;
      }
      if (r == VerifyResult.ERROR) {
        log.warn("Payment reconciliation stopped early: Paystack could not be reached");
        break;
      }
    }
    return new Summary(
        checked, settled, already, notPaid, pending, rejected, duplicate, needsRefund, errors, closed, refunding);
  }

  private void markChecked(Candidate c, Instant now) {
    try {
      if (c.attemptId() != null) {
        attempts.markChecked(c.attemptId(), now);
      } else {
        subscriptionPayments.markChecked(c.recordId(), now);
      }
    } catch (RuntimeException e) {
      log.warn("Could not record the check of reference {}: {}", c.reference(), e.toString());
    }
  }

  private List<Candidate> candidates(Instant now, Instant since, int limit) {
    if (limit == 0) return List.of();
    List<Candidate> all = new ArrayList<>();
    for (OnlinePaymentAttemptEntity a : attempts.findPendingSince(since, PageRequest.of(0, SCAN_LIMIT))) {
      if (isDue(a, now) && isOwn(a.kindName(), a.reference)) {
        all.add(new Candidate(a.kindName(), a.recordId, a.reference.trim(), a.id, a.lastCheckedAt, a.startedAt));
      }
    }
    for (SubscriptionOnlinePaymentEntity p :
        subscriptionPayments.findByStatusSince(
            SubscriptionOnlinePaymentEntity.STATUS_PENDING, since, PageRequest.of(0, SCAN_LIMIT))) {
      int checks = p.checkCount == null ? 0 : p.checkCount;
      if (isDue(p.lastCheckedAt, checks, now)
          && isOwn(OnlinePaymentSettlementService.KIND_SUBSCRIPTION, p.onlinePaymentReference)) {
        all.add(
            new Candidate(
                OnlinePaymentSettlementService.KIND_SUBSCRIPTION, p.id, p.onlinePaymentReference.trim(), null,
                p.lastCheckedAt, p.createdAt));
      }
    }
    all.sort(
        Comparator.comparing(Candidate::lastCheckedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Candidate::startedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    return all.size() > limit ? all.subList(0, limit) : all;
  }

  /** Never checked, or the backoff since the last check has passed. */
  static boolean isDue(OnlinePaymentAttemptEntity a, Instant now) {
    return isDue(a.lastCheckedAt, a.checkCount, now);
  }

  static boolean isDue(Instant lastCheckedAt, int checkCount, Instant now) {
    return lastCheckedAt == null || !now.isBefore(lastCheckedAt.plus(backoff(checkCount)));
  }

  /** {@code min(2 minutes x 2^checks, 30 minutes)}. */
  static Duration backoff(int checkCount) {
    int exponent = Math.max(0, Math.min(checkCount, 10));
    Duration d = FIRST_BACKOFF.multipliedBy(1L << exponent);
    return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
  }

  /** Only this app's own references for the matching kind; anything else is never sent to Paystack. */
  private static boolean isOwn(String kind, String reference) {
    return PaystackPaymentService.isOwnReference(reference)
        && kind.equals(OnlinePaymentSettlementService.kindOfReference(reference));
  }
}
