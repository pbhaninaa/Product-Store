package com.productstore.platform.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.services.PaystackPaymentService.VerifyResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Confirms online payments without any webhook: the Paystack account is shared with another app and its
 * single webhook URL may point elsewhere. Finds this app's online orders (pending, or cancelled by expiry),
 * bookings and subscription payments that are still waiting, have a reference and were started in the last
 * {@link #WINDOW}, and verifies each with Paystack through the same verify cache and idempotent settle path
 * as the return page. Only references this app created, carrying this record's metadata, are ever settled.
 */
@Service
public class OnlinePaymentReconciliationService {
  static final Duration WINDOW = Duration.ofHours(24);

  private static final Logger log = LoggerFactory.getLogger(OnlinePaymentReconciliationService.class);

  /** Counts for one run; logged as a single line. */
  public record Summary(int checked, int settled, int notPaid, int pending, int rejected, int errors) {}

  private record Candidate(String kind, UUID id, String reference, Instant startedAt) {}

  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final PaystackPaymentService paystack;

  public OnlinePaymentReconciliationService(
      OrderRepository orders,
      SalonBookingRepository bookings,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      PaystackPaymentService paystack) {
    this.orders = orders;
    this.bookings = bookings;
    this.subscriptionPayments = subscriptionPayments;
    this.paystack = paystack;
  }

  /**
   * Checks at most {@code limit} waiting payments, oldest first. Stops early when Paystack cannot be
   * reached (the next run tries again).
   */
  public Summary reconcile(Instant now, int limit) {
    List<Candidate> candidates = candidates(now.minus(WINDOW), Math.max(0, limit));
    int checked = 0, settled = 0, notPaid = 0, pending = 0, rejected = 0, errors = 0;
    for (Candidate c : candidates) {
      checked++;
      VerifyResult r = paystack.verifyAndSettle(c.reference(), c.kind(), c.id());
      switch (r) {
        case SETTLED -> settled++;
        case NOT_PAID -> notPaid++;
        case REJECTED -> rejected++;
        case ERROR -> errors++;
        default -> pending++;
      }
      if (r == VerifyResult.ERROR) {
        log.warn("Payment reconciliation stopped early: Paystack could not be reached");
        break;
      }
    }
    return new Summary(checked, settled, notPaid, pending, rejected, errors);
  }

  private List<Candidate> candidates(Instant since, int limit) {
    if (limit == 0) return List.of();
    PageRequest page = PageRequest.of(0, limit);
    List<Candidate> all = new ArrayList<>();
    for (OrderEntity o : orders.findOnlineAwaitingConfirmation(since, page)) {
      add(all, OnlinePaymentSettlementService.KIND_ORDER, o.id, o.onlinePaymentReference, o.createdAt);
    }
    for (SalonBookingEntity b : bookings.findOnlineAwaitingConfirmation(since, page)) {
      add(all, OnlinePaymentSettlementService.KIND_BOOKING, b.id, b.onlinePaymentReference, b.createdAt);
    }
    for (SubscriptionOnlinePaymentEntity p :
        subscriptionPayments.findByStatusSince(SubscriptionOnlinePaymentEntity.STATUS_PENDING, since, page)) {
      add(all, OnlinePaymentSettlementService.KIND_SUBSCRIPTION, p.id, p.onlinePaymentReference, p.createdAt);
    }
    all.sort(Comparator.comparing(Candidate::startedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    return all.size() > limit ? all.subList(0, limit) : all;
  }

  /** Only this app's own references for the matching kind; anything else is never sent to Paystack. */
  private static void add(List<Candidate> out, String kind, UUID id, String reference, Instant startedAt) {
    if (PaystackPaymentService.isOwnReference(reference)
        && kind.equals(OnlinePaymentSettlementService.kindOfReference(reference))) {
      out.add(new Candidate(kind, id, reference.trim(), startedAt));
    }
  }
}
