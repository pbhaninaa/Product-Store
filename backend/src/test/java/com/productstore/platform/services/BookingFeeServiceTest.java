package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonBookingEntity.BookingFeeReason;
import com.productstore.platform.entities.SalonBookingEntity.BookingFeeStatus;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Whether a booking carries a fee (FR-2, FR-3, BR-2, edge case 6), the refund reason and the receipt lines (FR-7). */
class BookingFeeServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");

  private final OnlinePaymentAvailability online = mock(OnlinePaymentAvailability.class);
  private final BookingFeeService service =
      new BookingFeeService(
          online,
          mock(OnlinePaymentAttemptRepository.class),
          mock(BookingFeeRefundService.class),
          mock(BookingFeeAudit.class),
          new StaticListableBeanFactory(Map.of("clock", Clock.fixed(NOW, ZoneOffset.UTC))).getBeanProvider(Clock.class));

  private static ShopSettingsEntity shop(Integer percent) {
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.bookingFeePercent = percent;
    return s;
  }

  @Test
  void cashBookingWithAFeeAndOnlinePaymentAvailableIsQuoted() {
    when(online.canCharge(20_000)).thenReturn(true);
    var q = service.quote(shop(20), SalonBookingEntity.ClientPaymentMethod.cash_store, new BigDecimal("1000.00"));
    assertThat(q).isPresent();
    assertThat(q.get().percent()).isEqualTo(20);
    assertThat(q.get().feeZar()).isEqualByComparingTo("200.00");
    assertThat(q.get().remainderZar()).isEqualByComparingTo("800.00");
  }

  @Test
  void noFeeForInAppBookingsZeroPercentUnavailablePaymentsOrBelowTheMinimum() {
    when(online.canCharge(anyLong())).thenReturn(true);
    assertThat(service.quote(shop(20), SalonBookingEntity.ClientPaymentMethod.online, new BigDecimal("1000"))).isEmpty();
    assertThat(service.quote(shop(0), SalonBookingEntity.ClientPaymentMethod.cash_store, new BigDecimal("1000"))).isEmpty();
    assertThat(service.quote(shop(null), SalonBookingEntity.ClientPaymentMethod.cash_store, new BigDecimal("1000"))).isEmpty();
    assertThat(service.quote(null, SalonBookingEntity.ClientPaymentMethod.cash_store, new BigDecimal("1000"))).isEmpty();
    assertThat(service.quote(shop(20), SalonBookingEntity.ClientPaymentMethod.cash_store, BigDecimal.ZERO)).isEmpty();
    when(online.canCharge(anyLong())).thenReturn(false); // Paystack off, cash-only demo, or below R1.00
    assertThat(service.quote(shop(20), SalonBookingEntity.ClientPaymentMethod.cash_store, new BigDecimal("1000"))).isEmpty();
  }

  @Test
  void refundReasonFollowsWhoCancelledAndWhen() {
    SalonBookingEntity b = booking(BookingFeeStatus.paid, SalonBookingEntity.Status.pending);
    b.startAt = NOW.plus(Duration.ofHours(24));
    assertThat(BookingFeeService.reasonFor(b, BookingFeeService.CancelledBy.CUSTOMER, NOW))
        .isEqualTo(BookingFeeReason.customer_cancellation);
    b.startAt = NOW.plus(Duration.ofHours(23));
    assertThat(BookingFeeService.reasonFor(b, BookingFeeService.CancelledBy.CUSTOMER, NOW))
        .isEqualTo(BookingFeeReason.late_cancellation);
    assertThat(BookingFeeService.reasonFor(b, BookingFeeService.CancelledBy.PROVIDER, NOW))
        .isEqualTo(BookingFeeReason.provider_cancellation);
    assertThat(BookingFeeService.reasonFor(b, BookingFeeService.CancelledBy.NO_SHOW, NOW))
        .isEqualTo(BookingFeeReason.no_show);
  }

  @Test
  void lateCancellationKeepsThePaidFeeWithoutARefund() {
    SalonBookingEntity b = booking(BookingFeeStatus.paid, SalonBookingEntity.Status.cancelled);
    b.startAt = NOW.plus(Duration.ofHours(2));
    service.onCancelled(b, BookingFeeService.CancelledBy.CUSTOMER, NOW);
    assertThat(b.bookingFeeStatus).isEqualTo(BookingFeeStatus.kept);
    assertThat(b.bookingFeeReason).isEqualTo(BookingFeeReason.late_cancellation);
    assertThat(b.bookingFeeOutcomeAt).isEqualTo(NOW);
  }

  @Test
  void cancellingBeforeTheFeeIsPaidClosesIt() {
    SalonBookingEntity b = booking(BookingFeeStatus.awaiting_payment, SalonBookingEntity.Status.cancelled);
    service.onCancelled(b, BookingFeeService.CancelledBy.PROVIDER, NOW);
    assertThat(b.bookingFeeStatus).isEqualTo(BookingFeeStatus.not_paid);
  }

  @Test
  void receiptLinesShowFeeShopAmountAndTotalSeparately() {
    SalonBookingEntity due = booking(BookingFeeStatus.awaiting_payment, SalonBookingEntity.Status.pending);
    assertThat(kinds(due)).containsExactly("booking_fee_due_online", "due_at_shop", "total");

    SalonBookingEntity paid = booking(BookingFeeStatus.paid, SalonBookingEntity.Status.pending);
    assertThat(kinds(paid)).containsExactly("booking_fee_online", "due_at_shop", "total");
    assertThat(amounts(paid)).containsExactly("200.00", "800.00", "1000.00");

    SalonBookingEntity full = booking(BookingFeeStatus.paid, SalonBookingEntity.Status.completed);
    assertThat(kinds(full)).containsExactly("booking_fee_online", "paid_at_shop", "total");

    assertThat(kinds(booking(BookingFeeStatus.refunded, SalonBookingEntity.Status.cancelled)))
        .containsExactly("booking_fee_online", "fee_refunded", "total");
    assertThat(kinds(booking(BookingFeeStatus.refund_pending, SalonBookingEntity.Status.cancelled)))
        .containsExactly("booking_fee_online", "fee_refund_in_progress", "total");
    assertThat(kinds(booking(BookingFeeStatus.kept, SalonBookingEntity.Status.cancelled)))
        .containsExactly("booking_fee_online", "fee_kept", "total");
    assertThat(kinds(booking(BookingFeeStatus.not_paid, SalonBookingEntity.Status.cancelled)))
        .containsExactly("total");
  }

  @Test
  void bookingsWithoutAFeeKeepTheirOldLines() {
    SalonBookingEntity cash = booking(null, SalonBookingEntity.Status.pending);
    cash.bookingFeeZar = null;
    assertThat(kinds(cash)).containsExactly("due_at_shop", "total");
    assertThat(BookingFeeService.amountDueAtShop(cash, cash.amountZar)).isEqualByComparingTo("1000.00");
    SalonBookingEntity online = booking(null, SalonBookingEntity.Status.confirmed);
    online.bookingFeeZar = null;
    online.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    assertThat(kinds(online)).containsExactly("paid_online", "total");
    assertThat(BookingFeeService.amountDueAtShop(online, online.amountZar)).isEqualByComparingTo("0");
    assertThat(BookingFeeService.describe(online, NOW)).isNull();
  }

  @Test
  void amountDueAtTheShopIsTheRemainderUntilFullyPaid() {
    SalonBookingEntity b = booking(BookingFeeStatus.paid, SalonBookingEntity.Status.pending);
    assertThat(BookingFeeService.amountDueAtShop(b, b.amountZar)).isEqualByComparingTo("800.00");
    b.status = SalonBookingEntity.Status.confirmed;
    assertThat(BookingFeeService.amountDueAtShop(b, b.amountZar)).isEqualByComparingTo("0.00");
  }

  private static SalonBookingEntity booking(BookingFeeStatus feeStatus, SalonBookingEntity.Status status) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = UUID.randomUUID();
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    b.status = status;
    b.amountZar = new BigDecimal("1000.00");
    b.bookingFeePercent = 20;
    b.bookingFeeZar = new BigDecimal("200.00");
    b.bookingFeeStatus = feeStatus;
    b.startAt = NOW.plus(Duration.ofDays(2));
    return b;
  }

  private static List<String> kinds(SalonBookingEntity b) {
    return BookingFeeService.receiptLines(b, b.amountZar).stream().map(m -> (String) m.get("kind")).toList();
  }

  private static List<String> amounts(SalonBookingEntity b) {
    return BookingFeeService.receiptLines(b, b.amountZar).stream().map(m -> (String) m.get("amountZar")).toList();
  }
}
