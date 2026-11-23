package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.services.PaystackPaymentService.VerifyResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class OnlinePaymentReconciliationServiceTest {
  private final Instant now = Instant.parse("2026-11-23T10:00:00Z");
  private OnlinePaymentAttemptRepository attempts;
  private SubscriptionOnlinePaymentRepository subscriptionPayments;
  private PaystackPaymentService paystack;
  private OnlinePaymentReconciliationService reconciliation;

  @BeforeEach
  void setUp() {
    attempts = mock(OnlinePaymentAttemptRepository.class);
    subscriptionPayments = mock(SubscriptionOnlinePaymentRepository.class);
    paystack = mock(PaystackPaymentService.class);
    when(attempts.findPendingSince(any(), any())).thenReturn(List.of());
    when(subscriptionPayments.findByStatusSince(any(), any(), any())).thenReturn(List.of());
    when(paystack.verifyAndSettle(anyString(), anyString(), any())).thenReturn(VerifyResult.PENDING);
    reconciliation = new OnlinePaymentReconciliationService(attempts, subscriptionPayments, paystack);
  }

  @Test
  void checksAttemptsAndSubscriptionPaymentsOfTheLast24HoursAndCountsEachResult() {
    OnlinePaymentAttemptEntity order = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORDAAAABBBBCCCCDDDD", 3, null, 0);
    OnlinePaymentAttemptEntity booking = attempt(OnlinePaymentAttemptEntity.Kind.BOOKING, "BKGAAAABBBBCCCCDDDD", 5, null, 0);
    OnlinePaymentAttemptEntity paidAgain = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORDEEEEFFFFGGGGHHHH", 6, null, 0);
    SubscriptionOnlinePaymentEntity sub = subscription("SUBAAAABBBBCCCCDDDD", 1);
    when(attempts.findPendingSince(any(), any())).thenReturn(List.of(paidAgain, booking, order));
    when(subscriptionPayments.findByStatusSince(eq(SubscriptionOnlinePaymentEntity.STATUS_PENDING), any(), any()))
        .thenReturn(List.of(sub));
    when(attempts.closeExpired(any(), any())).thenReturn(2);
    when(paystack.verifyAndSettle("ORDAAAABBBBCCCCDDDD", "order", order.recordId)).thenReturn(VerifyResult.SETTLED);
    when(paystack.verifyAndSettle("BKGAAAABBBBCCCCDDDD", "booking", booking.recordId)).thenReturn(VerifyResult.NOT_PAID);
    when(paystack.verifyAndSettle("ORDEEEEFFFFGGGGHHHH", "order", paidAgain.recordId))
        .thenReturn(VerifyResult.ALREADY_SETTLED);

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary).isEqualTo(new OnlinePaymentReconciliationService.Summary(4, 1, 1, 1, 1, 0, 0, 0, 0, 2));
    ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
    verify(attempts).findPendingSince(since.capture(), any());
    assertThat(since.getValue()).isEqualTo(now.minus(Duration.ofHours(24)));
    verify(attempts).closeExpired(now.minus(Duration.ofHours(24)), now);
    // Every attempt check is recorded (for the backoff); subscription rows have no attempt.
    verify(attempts).markChecked(order.id, now);
    verify(attempts).markChecked(booking.id, now);
    verify(attempts).markChecked(paidAgain.id, now);
  }

  @Test
  void neverCheckedFirstThenLeastRecentlyCheckedAndBackoffSkipsAttemptsNotDue() {
    // checked 1 minute ago after 0 checks: due after 2 minutes, so not yet.
    OnlinePaymentAttemptEntity recent = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000001", 9, 1, 0);
    // checked 10 minutes ago after 2 checks: due after 8 minutes.
    OnlinePaymentAttemptEntity due = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000002", 8, 10, 2);
    // checked 20 minutes ago after 5 checks: backoff capped at 30 minutes, so not due.
    OnlinePaymentAttemptEntity capped = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000003", 7, 20, 5);
    // never checked: first, even though it is the newest.
    OnlinePaymentAttemptEntity fresh = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000004", 1, null, 0);
    when(attempts.findPendingSince(any(), any())).thenReturn(List.of(recent, due, capped, fresh));

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.checked()).isEqualTo(2);
    var order = inOrder(paystack);
    order.verify(paystack).verifyAndSettle("ORD0000000000000004", "order", fresh.recordId);
    order.verify(paystack).verifyAndSettle("ORD0000000000000002", "order", due.recordId);
    verify(paystack, never()).verifyAndSettle(eq("ORD0000000000000001"), anyString(), any());
    verify(paystack, never()).verifyAndSettle(eq("ORD0000000000000003"), anyString(), any());
  }

  /** Minor 3: subscription payments back off like attempts, and each check is recorded on them. */
  @Test
  void subscriptionPaymentsBackOffAndRecordTheirChecks() {
    SubscriptionOnlinePaymentEntity fresh = subscription("SUB0000000000000001", 2);
    SubscriptionOnlinePaymentEntity recent = subscription("SUB0000000000000002", 3);
    recent.lastCheckedAt = now.minus(Duration.ofMinutes(1));
    recent.checkCount = 0;
    SubscriptionOnlinePaymentEntity due = subscription("SUB0000000000000003", 4);
    due.lastCheckedAt = now.minus(Duration.ofMinutes(5));
    due.checkCount = 1; // 4 minute backoff
    when(subscriptionPayments.findByStatusSince(eq(SubscriptionOnlinePaymentEntity.STATUS_PENDING), any(), any()))
        .thenReturn(List.of(fresh, recent, due));

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.checked()).isEqualTo(2);
    var order = inOrder(paystack);
    order.verify(paystack).verifyAndSettle("SUB0000000000000001", "subscription", fresh.id);
    order.verify(paystack).verifyAndSettle("SUB0000000000000003", "subscription", due.id);
    verify(paystack, never()).verifyAndSettle(eq("SUB0000000000000002"), anyString(), any());
    verify(subscriptionPayments).markChecked(fresh.id, now);
    verify(subscriptionPayments).markChecked(due.id, now);
    verify(subscriptionPayments, never()).markChecked(recent.id, now);
  }

  @Test
  void backoffDoublesUpToThirtyMinutes() {
    assertThat(OnlinePaymentReconciliationService.backoff(0)).isEqualTo(Duration.ofMinutes(2));
    assertThat(OnlinePaymentReconciliationService.backoff(1)).isEqualTo(Duration.ofMinutes(4));
    assertThat(OnlinePaymentReconciliationService.backoff(3)).isEqualTo(Duration.ofMinutes(16));
    assertThat(OnlinePaymentReconciliationService.backoff(4)).isEqualTo(Duration.ofMinutes(30));
    assertThat(OnlinePaymentReconciliationService.backoff(500)).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void otherAppsReferencesAreNeverSentToPaystack() {
    when(attempts.findPendingSince(any(), any()))
        .thenReturn(
            List.of(
                attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "T685312322670591", 3, null, 0),
                attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "BKGAAAABBBBCCCCDDDD", 2, null, 0)));

    assertThat(reconciliation.reconcile(now, 50).checked()).isZero();
    verify(paystack, never()).verifyAndSettle(anyString(), anyString(), any());
  }

  @Test
  void batchLimitIsRespected() {
    List<OnlinePaymentAttemptEntity> many = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      many.add(attempt(OnlinePaymentAttemptEntity.Kind.ORDER, String.format("ORD%016d", i), 20 - i, null, 0));
    }
    when(attempts.findPendingSince(any(), any())).thenReturn(many);

    var summary = reconciliation.reconcile(now, 4);

    assertThat(summary.checked()).isEqualTo(4);
    verify(paystack, times(4)).verifyAndSettle(anyString(), eq("order"), any());
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(attempts).findPendingSince(any(), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(OnlinePaymentReconciliationService.SCAN_LIMIT);
  }

  /** Minor 2: one candidate failing does not stop the others. */
  @Test
  void aFailureOnOneCandidateIsCountedAndTheRunGoesOn() {
    OnlinePaymentAttemptEntity broken = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000001", 3, null, 0);
    OnlinePaymentAttemptEntity fine = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000002", 2, null, 0);
    when(attempts.findPendingSince(any(), any())).thenReturn(List.of(broken, fine));
    when(paystack.verifyAndSettle(eq("ORD0000000000000001"), anyString(), any()))
        .thenThrow(new IllegalStateException("lock timeout"));
    when(paystack.verifyAndSettle(eq("ORD0000000000000002"), anyString(), any())).thenReturn(VerifyResult.SETTLED);

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.settled()).isEqualTo(1);
    verify(attempts).markChecked(broken.id, now);
    verify(attempts).markChecked(fine.id, now);
  }

  @Test
  void runStopsWhenPaystackCannotBeReached() {
    OnlinePaymentAttemptEntity first = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000001", 3, null, 0);
    OnlinePaymentAttemptEntity second = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, "ORD0000000000000002", 2, null, 0);
    when(attempts.findPendingSince(any(), any())).thenReturn(List.of(first, second));
    when(paystack.verifyAndSettle(eq("ORD0000000000000001"), anyString(), any())).thenReturn(VerifyResult.ERROR);

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.checked()).isEqualTo(1);
    verify(paystack, never()).verifyAndSettle(eq("ORD0000000000000002"), anyString(), any());
  }

  /** An attempt started {@code hoursAgo}; checked {@code checkedMinutesAgo} minutes ago (null = never). */
  private OnlinePaymentAttemptEntity attempt(
      OnlinePaymentAttemptEntity.Kind kind, String reference, int hoursAgo, Integer checkedMinutesAgo, int checks) {
    OnlinePaymentAttemptEntity a = new OnlinePaymentAttemptEntity();
    a.id = UUID.randomUUID();
    a.kind = kind;
    a.recordId = UUID.randomUUID();
    a.reference = reference;
    a.startedAt = now.minus(Duration.ofHours(hoursAgo));
    a.lastCheckedAt = checkedMinutesAgo == null ? null : now.minus(Duration.ofMinutes(checkedMinutesAgo));
    a.checkCount = checks;
    return a;
  }

  private SubscriptionOnlinePaymentEntity subscription(String reference, int hoursAgo) {
    SubscriptionOnlinePaymentEntity p = new SubscriptionOnlinePaymentEntity();
    p.id = UUID.randomUUID();
    p.onlinePaymentReference = reference;
    p.createdAt = now.minus(Duration.ofHours(hoursAgo));
    return p;
  }
}
