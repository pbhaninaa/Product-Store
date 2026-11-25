package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentRefundEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OnlinePaymentRefundRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.SalonBookingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

/** Retry backoff, claim and webhook rules of booking fee refunds (edge case 5, NFR-4). */
class BookingFeeRefundServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");

  private final OnlinePaymentRefundRepository refunds = mock(OnlinePaymentRefundRepository.class);
  private final OnlinePaymentAttemptRepository attempts = mock(OnlinePaymentAttemptRepository.class);
  private final PaystackClient paystack = mock(PaystackClient.class);
  private final BookingFeeRefundService service =
      new BookingFeeRefundService(
          refunds,
          attempts,
          mock(SalonBookingRepository.class),
          mock(ProviderPayoutLineRepository.class),
          mock(BookingFeeAudit.class),
          paystack,
          mock(PlatformTransactionManager.class),
          new StaticListableBeanFactory(Map.of("clock", Clock.fixed(NOW, ZoneOffset.UTC))).getBeanProvider(Clock.class));

  @Test
  void backoffDoublesFromFiveMinutesAndStopsAtSixHours() {
    assertThat(BookingFeeRefundService.backoff(1)).isEqualTo(Duration.ofMinutes(5));
    assertThat(BookingFeeRefundService.backoff(2)).isEqualTo(Duration.ofMinutes(10));
    assertThat(BookingFeeRefundService.backoff(4)).isEqualTo(Duration.ofMinutes(40));
    assertThat(BookingFeeRefundService.backoff(7)).isEqualTo(Duration.ofMinutes(320));
    assertThat(BookingFeeRefundService.backoff(8)).isEqualTo(Duration.ofHours(6));
    assertThat(BookingFeeRefundService.backoff(500)).isEqualTo(Duration.ofHours(6));
  }

  @Test
  void aRefundThatIsNotDueOrAlreadyAcceptedIsNeverSent() {
    OnlinePaymentRefundEntity notDue = refund(OnlinePaymentRefundEntity.Status.PENDING, NOW.plusSeconds(60));
    when(refunds.lockById(notDue.id)).thenReturn(Optional.of(notDue));
    assertThat(service.send(notDue.id)).isEqualTo(BookingFeeRefundService.SendResult.NOT_DUE);

    OnlinePaymentRefundEntity accepted = refund(OnlinePaymentRefundEntity.Status.SUBMITTED, null);
    when(refunds.lockById(accepted.id)).thenReturn(Optional.of(accepted));
    assertThat(service.send(accepted.id)).isEqualTo(BookingFeeRefundService.SendResult.NOT_DUE);

    OnlinePaymentRefundEntity manual = refund(OnlinePaymentRefundEntity.Status.MANUAL, NOW.minusSeconds(60));
    when(refunds.lockById(manual.id)).thenReturn(Optional.of(manual));
    assertThat(service.send(manual.id)).isEqualTo(BookingFeeRefundService.SendResult.NOT_DUE);

    verify(paystack, never()).refund(any());
  }

  @Test
  void refundNotificationsForUnknownReferencesOrOtherStatusesChangeNothing() {
    assertThat(service.onRefundNotification("BKGUNKNOWN0000000000", "processed")).isFalse();
    assertThat(service.onRefundNotification("BKGUNKNOWN0000000000", "pending")).isFalse();
    assertThat(service.onRefundNotification(null, "processed")).isFalse();

    OnlinePaymentAttemptEntity a = new OnlinePaymentAttemptEntity();
    a.id = UUID.randomUUID();
    when(attempts.findByReference("ORDNOTAFEE0000000000")).thenReturn(Optional.of(a));
    when(refunds.findByPaymentAttemptId(a.id)).thenReturn(Optional.empty());
    assertThat(service.onRefundNotification("ORDNOTAFEE0000000000", "processed")).isFalse();
  }

  private static OnlinePaymentRefundEntity refund(OnlinePaymentRefundEntity.Status status, Instant nextTryAt) {
    OnlinePaymentRefundEntity r = new OnlinePaymentRefundEntity();
    r.id = UUID.randomUUID();
    r.status = status;
    r.nextTryAt = nextTryAt;
    r.paymentReference = "BKGAAAABBBBCCCCDDDD";
    r.amountCents = 20_000;
    return r;
  }
}
