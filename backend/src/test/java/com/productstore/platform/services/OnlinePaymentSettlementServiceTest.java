package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.services.OnlinePaymentSettlementService.ConfirmedPayment;
import com.productstore.platform.services.OnlinePaymentSettlementService.Outcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OnlinePaymentSettlementServiceTest {
  private OnlinePaymentAttemptRepository attempts;
  private OrderRepository orders;
  private SalonBookingRepository bookings;
  private SalonServiceRepository services;
  private SubscriptionOnlinePaymentRepository subscriptionPayments;
  private MerchantSubscriptionRepository merchantSubscriptions;
  private CheckoutService checkout;
  private SalonBookingService salon;
  private MerchantSubscriptionService subscriptions;
  private OnlinePaymentSettlementService settlement;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    orders = mock(OrderRepository.class);
    bookings = mock(SalonBookingRepository.class);
    services = mock(SalonServiceRepository.class);
    subscriptionPayments = mock(SubscriptionOnlinePaymentRepository.class);
    merchantSubscriptions = mock(MerchantSubscriptionRepository.class);
    checkout = mock(CheckoutService.class);
    salon = mock(SalonBookingService.class);
    subscriptions = mock(MerchantSubscriptionService.class);
    when(orders.lockByOnlinePaymentReference(any())).thenReturn(List.of());
    when(bookings.lockByOnlinePaymentReference(any())).thenReturn(List.of());
    when(subscriptionPayments.lockByOnlinePaymentReference(any())).thenReturn(Optional.empty());
    when(orders.lockById(any())).thenReturn(Optional.empty());
    when(bookings.lockById(any())).thenReturn(Optional.empty());
    when(subscriptionPayments.lockById(any())).thenReturn(Optional.empty());
    attempts = mock(OnlinePaymentAttemptRepository.class);
    when(attempts.findByReference(any())).thenReturn(Optional.empty());
    when(attempts.lockById(any())).thenReturn(Optional.empty());
    when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(checkout.canDeductStock(any())).thenReturn(true);
    settlement =
        new OnlinePaymentSettlementService(
            attempts, orders, bookings, services, subscriptionPayments, merchantSubscriptions, checkout, salon,
            subscriptions);
  }

  // ------------------------------------------------------------------ orders

  @Test
  void settlesOrderFoundByReference() {
    OrderEntity o = order("ORDREF1", "150.50");
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    Outcome out = settlement.settle(paid("ORDREF1", 15050, "ZAR", null, null));

    assertThat(out).isEqualTo(Outcome.SETTLED);
    assertThat(o.onlinePaymentProviderId).isEqualTo("4099");
    assertThat(o.onlinePaymentChannel).isEqualTo(OnlinePaymentChannel.CARD);
    verify(checkout).finalizeOnlinePaidOrder(o);
  }

  @Test
  void repeatDeliveryForPaidOrderIsIdempotent() {
    OrderEntity o = order("ORDREF1", "100.00");
    o.status = OrderEntity.OrderStatus.paid;
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThat(settlement.settle(paid("ORDREF1", 10000, "ZAR", null, null))).isEqualTo(Outcome.ALREADY_SETTLED);
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
  }

  @Test
  void amountMismatchIsRejectedAndNothingIsPaid() {
    OrderEntity o = order("ORDREF1", "100.00");
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThatThrownBy(() -> settlement.settle(paid("ORDREF1", 9999, "ZAR", null, null)))
        .hasMessage("online_payment_amount_mismatch");
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
  }

  @Test
  void wrongCurrencyIsRejected() {
    OrderEntity o = order("ORDREF1", "100.00");
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThatThrownBy(() -> settlement.settle(paid("ORDREF1", 10000, "NGN", null, null)))
        .hasMessage("online_payment_currency_mismatch");
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
  }

  @Test
  void unknownReferenceFallsBackToMetadataAndRecordsThePaidReference() {
    OrderEntity o = order("ORDNEWER", "80.00");
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    Outcome out = settlement.settle(paid("ORDOLDER", 8000, "ZAR", "order", o.id.toString()));

    assertThat(out).isEqualTo(Outcome.SETTLED);
    assertThat(o.onlinePaymentReference).isEqualTo("ORDOLDER");
    verify(checkout).finalizeOnlinePaidOrder(o);
  }

  @Test
  void paymentForUnknownRecordIsNotFound() {
    assertThat(settlement.settle(paid("NOPE", 100, "ZAR", "order", UUID.randomUUID().toString())))
        .isEqualTo(Outcome.NOT_FOUND);
    assertThat(settlement.settle(paid("NOPE", 100, "ZAR", null, null))).isEqualTo(Outcome.NOT_FOUND);
  }

  @Test
  void nonOnlineOrderIsRefused() {
    OrderEntity o = order("ORDREF1", "100.00");
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThatThrownBy(() -> settlement.settle(paid("ORDREF1", 10000, "ZAR", null, null)))
        .hasMessage("not_online_order");
  }

  // ------------------------------------------------------------------ bookings

  @Test
  void settlesBookingAtThePriceFixedWhenBooked() {
    SalonBookingEntity b = booking("BKGREF1");
    b.amountZar = new BigDecimal("250.00");
    when(bookings.lockByOnlinePaymentReference("BKGREF1")).thenReturn(List.of(b));
    when(services.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(service("300.00")));

    assertThatThrownBy(() -> settlement.settle(paid("BKGREF1", 30000, "ZAR", null, null)))
        .hasMessage("online_payment_amount_mismatch");
    assertThat(settlement.settle(paid("BKGREF1", 25000, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);
    verify(salon).finalizeOnlinePaidBooking(b);
  }

  @Test
  void bookingWithoutFixedAmountPaysPriceLessDiscount() {
    SalonBookingEntity b = booking("BKGREF2");
    b.discountZar = new BigDecimal("20.00");
    when(bookings.lockByOnlinePaymentReference("BKGREF2")).thenReturn(List.of(b));
    when(services.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(service("120.00")));

    assertThat(settlement.settle(paid("BKGREF2", 10000, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);
  }

  @Test
  void confirmedBookingRepeatIsIdempotent() {
    SalonBookingEntity b = booking("BKGREF1");
    b.status = SalonBookingEntity.Status.in_progress;
    b.amountZar = new BigDecimal("50.00");
    when(bookings.lockByOnlinePaymentReference("BKGREF1")).thenReturn(List.of(b));
    when(services.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(service("50.00")));

    assertThat(settlement.settle(paid("BKGREF1", 5000, "ZAR", null, null))).isEqualTo(Outcome.ALREADY_SETTLED);
    verify(salon, never()).finalizeOnlinePaidBooking(any());
  }

  // ------------------------------------------------------------------ subscriptions

  @Test
  void settlesSubscriptionOnceAndCopiesDetailsToTheSubscription() {
    SubscriptionOnlinePaymentEntity p = subscriptionPayment("SUBREF1", "199.00");
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    sub.tenantId = tenantId;
    when(subscriptionPayments.lockByOnlinePaymentReference("SUBREF1")).thenReturn(Optional.of(p));
    when(merchantSubscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(sub));

    assertThat(settlement.settle(paid("SUBREF1", 19900, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);
    assertThat(settlement.settle(paid("SUBREF1", 19900, "ZAR", null, null))).isEqualTo(Outcome.ALREADY_SETTLED);

    assertThat(p.status).isEqualTo(SubscriptionOnlinePaymentEntity.STATUS_COMPLETED);
    assertThat(p.completedAt).isNotNull();
    assertThat(sub.onlinePaymentReference).isEqualTo("SUBREF1");
    assertThat(sub.onlinePaymentProviderId).isEqualTo("4099");
    verify(subscriptions).finalizeOnlinePaidSubscription(tenantId, TenantEntity.SubscriptionPlan.STANDARD);
  }

  @Test
  void subscriptionAmountMismatchNeverActivates() {
    SubscriptionOnlinePaymentEntity p = subscriptionPayment("SUBREF1", "199.00");
    when(subscriptionPayments.lockByOnlinePaymentReference("SUBREF1")).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> settlement.settle(paid("SUBREF1", 19800, "ZAR", null, null)))
        .hasMessage("online_payment_amount_mismatch");
    assertThat(p.status).isEqualTo(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    verify(subscriptions, never()).finalizeOnlinePaidSubscription(any(), any());
  }

  // ------------------------------------------------------------------ routing and log markers

  @Test
  void referencePrefixPicksTheTableToLookIn() {
    SubscriptionOnlinePaymentEntity p = subscriptionPayment("SUBREF9", "10.00");
    when(subscriptionPayments.lockByOnlinePaymentReference("SUBREF9")).thenReturn(Optional.of(p));

    settlement.settle(paid("SUBREF9", 1000, "ZAR", null, null));

    verify(orders, never()).lockByOnlinePaymentReference(any());
    verify(bookings, never()).lockByOnlinePaymentReference(any());
    assertThat(OnlinePaymentSettlementService.lookupOrder("booking", null)).containsExactly("booking", "order", "subscription");
    assertThat(OnlinePaymentSettlementService.lookupOrder(null, "subscription"))
        .containsExactly("subscription", "order", "booking");
    assertThat(OnlinePaymentSettlementService.lookupOrder(null, null)).containsExactly("order", "booking", "subscription");
  }

  @Test
  void paymentForAnOrderTheMerchantCancelledIsFlaggedForRefund(CapturedOutput output) {
    OrderEntity o = order("ORDREF1", "100.00");
    o.status = OrderEntity.OrderStatus.cancelled;
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThatThrownBy(() -> settlement.settle(paid("ORDREF1", 10000, "ZAR", null, null))).hasMessage("order_not_pending");
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
    assertThat(output.getOut()).contains("ONLINE_PAYMENT_NEEDS_REFUND").doesNotContain("buyer");
  }

  @Test
  void latePaymentRevivesAnOrderCancelledByExpiry() {
    OrderEntity o = order("ORDREF1", "100.00");
    o.status = OrderEntity.OrderStatus.cancelled;
    o.expiredAt = java.time.Instant.now();
    when(orders.lockByOnlinePaymentReference("ORDREF1")).thenReturn(List.of(o));

    assertThat(settlement.settle(paid("ORDREF1", 10000, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);
    verify(checkout).finalizeOnlinePaidOrder(o);
  }

  @Test
  void paymentForACancelledBookingIsFlaggedForRefund(CapturedOutput output) {
    SalonBookingEntity b = booking("BKGREF1");
    b.status = SalonBookingEntity.Status.cancelled;
    b.amountZar = new BigDecimal("50.00");
    when(bookings.lockByOnlinePaymentReference("BKGREF1")).thenReturn(List.of(b));
    when(services.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(service("50.00")));

    assertThatThrownBy(() -> settlement.settle(paid("BKGREF1", 5000, "ZAR", null, null))).hasMessage("booking_not_pending");
    verify(salon, never()).finalizeOnlinePaidBooking(any());
    assertThat(output.getOut()).contains("ONLINE_PAYMENT_NEEDS_REFUND");
  }

  @Test
  void secondDifferentPaymentForAPaidOrderIsLoggedAsPossibleDuplicate(CapturedOutput output) {
    OrderEntity o = order("ORDNEW", "100.00");
    o.status = OrderEntity.OrderStatus.paid;
    o.onlinePaymentProviderId = "1111";
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    assertThat(settlement.settle(paid("ORDOLD", 10000, "ZAR", "order", o.id.toString())))
        .isEqualTo(Outcome.ALREADY_SETTLED);
    assertThat(output.getOut()).contains("Possible duplicate online payment").contains("ORDOLD");
  }

  // ------------------------------------------------------------------ attempt ledger

  @Test
  void attemptPaymentSettlesTheOrderAndMarksTheAttemptPaid() {
    OrderEntity o = order("ORDLATEST", "100.00");
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDEARLIER");
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    assertThat(settlement.settle(paid("ORDEARLIER", 10000, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);

    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.PAID);
    assertThat(a.outcomeAt).isNotNull();
    assertThat(o.onlinePaymentReference).isEqualTo("ORDEARLIER");
    assertThat(o.onlinePaymentProviderId).isEqualTo("4099");
    verify(checkout).finalizeOnlinePaidOrder(o);

    // The same payment again: nothing more happens.
    assertThat(settlement.settle(paid("ORDEARLIER", 10000, "ZAR", null, null))).isEqualTo(Outcome.ALREADY_SETTLED);
    verify(checkout, times(1)).finalizeOnlinePaidOrder(any());
  }

  @Test
  void secondAttemptPaidForAnAlreadyPaidOrderIsRecordedAsDuplicateOnce(CapturedOutput output) {
    OrderEntity o = order("ORDFIRST", "100.00");
    o.status = OrderEntity.OrderStatus.paid;
    o.onlinePaymentProviderId = "1111";
    OnlinePaymentAttemptEntity second = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDSECOND");
    stubAttempt(second);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    assertThat(settlement.settle(paid("ORDSECOND", 10000, "ZAR", null, null))).isEqualTo(Outcome.DUPLICATE);
    assertThat(settlement.settle(paid("ORDSECOND", 10000, "ZAR", null, null))).isEqualTo(Outcome.DUPLICATE);

    assertThat(second.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.DUPLICATE);
    assertThat(countOf(output.getOut(), "Possible duplicate online payment")).isEqualTo(1);
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
  }

  @Test
  void latePaymentForACancelledBookingNeedsRefundAndIsLoggedOnce(CapturedOutput output) {
    SalonBookingEntity b = booking("BKGLATE");
    b.status = SalonBookingEntity.Status.cancelled;
    b.amountZar = new BigDecimal("50.00");
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, "BKGLATE");
    stubAttempt(a);
    when(bookings.lockById(b.id)).thenReturn(Optional.of(b));
    when(services.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(service("50.00")));

    assertThat(settlement.settle(paid("BKGLATE", 5000, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(settlement.settle(paid("BKGLATE", 5000, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(settlement.settle(paid("BKGLATE", 5000, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);

    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND);
    assertThat(countOf(output.getOut(), "ONLINE_PAYMENT_NEEDS_REFUND")).isEqualTo(1);
    verify(salon, never()).finalizeOnlinePaidBooking(any());
  }

  @Test
  void latePaymentRevivesAnExpiredOrderThroughItsAttempt() {
    OrderEntity o = order("ORDX", "100.00");
    o.status = OrderEntity.OrderStatus.cancelled;
    o.expiredAt = java.time.Instant.now();
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDX");
    a.outcome = OnlinePaymentAttemptEntity.Outcome.NOT_PAID; // its window had closed
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    assertThat(settlement.settle(paid("ORDX", 10000, "ZAR", null, null))).isEqualTo(Outcome.SETTLED);
    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.PAID);
    verify(checkout).finalizeOnlinePaidOrder(o);
  }

  @Test
  void wrongAmountOnAnAttemptNeedsRefundInsteadOfFailing() {
    OrderEntity o = order("ORDX", "100.00");
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDX");
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    assertThat(settlement.settle(paid("ORDX", 9999, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(o.status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  /** Minor 3: with both amounts, the charge must cover what was asked, and what was asked must match. */
  @Test
  void chargeBelowTheRequestedAmountIsNotAcceptedAsPayment() {
    OrderEntity o = order("ORDX", "100.00");
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDX");
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));

    ConfirmedPayment shortCharge =
        new ConfirmedPayment("ORDX", "4099", 10000, false, "ZAR", OnlinePaymentChannel.CARD, null, null);
    assertThat(settlement.settle(shortCharge)).isEqualTo(Outcome.NEEDS_REFUND);
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
  }

  /** Major: a late payment reviving an expired order whose stock sold out is recorded, never half applied. */
  @Test
  void latePaymentForAnExpiredOrderWhoseStockSoldOutNeedsRefund(CapturedOutput output) {
    OrderEntity o = order("ORDX", "100.00");
    o.status = OrderEntity.OrderStatus.cancelled;
    o.expiredAt = java.time.Instant.now();
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDX");
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));
    when(checkout.canDeductStock(o)).thenReturn(false);

    assertThat(settlement.settle(paid("ORDX", 10000, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(settlement.settle(paid("ORDX", 10000, "ZAR", null, null))).isEqualTo(Outcome.NEEDS_REFUND);

    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND);
    assertThat(o.status).isEqualTo(OrderEntity.OrderStatus.cancelled);
    verify(checkout, never()).finalizeOnlinePaidOrder(any());
    assertThat(countOf(output.getOut(), "ONLINE_PAYMENT_NEEDS_REFUND")).isEqualTo(1);
  }

  /** Safety net: an unexpected finalize failure leaves the transaction to roll back and asks for a refund. */
  @Test
  void unexpectedFinalizeFailureIsReportedForRefundOutsideTheTransaction() {
    OrderEntity o = order("ORDX", "100.00");
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, "ORDX");
    stubAttempt(a);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("insufficient_stock_on_confirm"))
        .when(checkout)
        .finalizeOnlinePaidOrder(o);

    assertThatThrownBy(() -> settlement.settle(paid("ORDX", 10000, "ZAR", null, null)))
        .isInstanceOf(OnlinePaymentSettlementService.AttemptCannotBeAppliedException.class)
        .satisfies(
            e -> assertThat(((OnlinePaymentSettlementService.AttemptCannotBeAppliedException) e).attemptId())
                .isEqualTo(a.id));
  }

  @Test
  void recordNeedsRefundMarksThePendingAttemptOnceAndKeepsPaidOnes(CapturedOutput output) {
    OnlinePaymentAttemptEntity a = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, UUID.randomUUID(), "ORDX");
    when(attempts.lockById(a.id)).thenReturn(Optional.of(a));

    assertThat(settlement.recordNeedsRefund(a.id, "ORDX", "boom")).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(settlement.recordNeedsRefund(a.id, "ORDX", "boom")).isEqualTo(Outcome.NEEDS_REFUND);
    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND);
    assertThat(countOf(output.getOut(), "ONLINE_PAYMENT_NEEDS_REFUND")).isEqualTo(1);

    OnlinePaymentAttemptEntity paidOne = attempt(OnlinePaymentAttemptEntity.Kind.ORDER, UUID.randomUUID(), "ORDY");
    paidOne.outcome = OnlinePaymentAttemptEntity.Outcome.PAID;
    when(attempts.lockById(paidOne.id)).thenReturn(Optional.of(paidOne));
    assertThat(settlement.recordNeedsRefund(paidOne.id, "ORDY", "boom")).isEqualTo(Outcome.ALREADY_SETTLED);
  }

  /** Minor 1: only ORD/BKG references touch the ledger, and only an existing attempt is locked. */
  @Test
  void attemptsAreLockedOnlyWhenTheyExist() {
    settlement.settle(paid("SUBREF1", 1000, "ZAR", null, null));
    verify(attempts, never()).findByReference("SUBREF1");

    settlement.settle(paid("ORDUNKNOWN", 1000, "ZAR", null, null));
    verify(attempts).findByReference("ORDUNKNOWN");
    verify(attempts, never()).lockById(any());
  }

  @Test
  void toCentsIsExact() {
    assertThat(OnlinePaymentSettlementService.toCents(new BigDecimal("150.5"))).isEqualTo(15050);
    assertThat(OnlinePaymentSettlementService.toCents(new BigDecimal("0.01"))).isEqualTo(1);
  }

  // ------------------------------------------------------------------ helpers

  private static ConfirmedPayment paid(String ref, long cents, String currency, String kind, String id) {
    return new ConfirmedPayment(ref, "4099", cents, currency, OnlinePaymentChannel.CARD, kind, id);
  }

  private OnlinePaymentAttemptEntity attempt(OnlinePaymentAttemptEntity.Kind kind, UUID recordId, String reference) {
    OnlinePaymentAttemptEntity a = new OnlinePaymentAttemptEntity();
    a.id = UUID.randomUUID();
    a.kind = kind;
    a.recordId = recordId;
    a.tenantId = tenantId;
    a.reference = reference;
    a.startedAt = java.time.Instant.now();
    a.outcome = OnlinePaymentAttemptEntity.Outcome.PENDING;
    return a;
  }

  /** The attempt is found by reference, then locked by id. */
  private void stubAttempt(OnlinePaymentAttemptEntity a) {
    when(attempts.findByReference(a.reference)).thenReturn(Optional.of(a));
    when(attempts.lockById(a.id)).thenReturn(Optional.of(a));
  }

  private static int countOf(String text, String needle) {
    int count = 0;
    for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) count++;
    return count;
  }

  private OrderEntity order(String reference, String total) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.paymentMethod = OrderEntity.PaymentMethod.online;
    o.status = OrderEntity.OrderStatus.pending_payment;
    o.totalZar = new BigDecimal(total);
    o.onlinePaymentReference = reference;
    return o;
  }

  private SalonBookingEntity booking(String reference) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = UUID.randomUUID();
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    b.status = SalonBookingEntity.Status.pending;
    b.onlinePaymentReference = reference;
    return b;
  }

  private static SalonServiceEntity service(String price) {
    SalonServiceEntity s = new SalonServiceEntity();
    s.priceZar = new BigDecimal(price);
    return s;
  }

  private SubscriptionOnlinePaymentEntity subscriptionPayment(String reference, String amount) {
    SubscriptionOnlinePaymentEntity p = new SubscriptionOnlinePaymentEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    p.amount = new BigDecimal(amount);
    p.status = SubscriptionOnlinePaymentEntity.STATUS_PENDING;
    p.onlinePaymentChannel = OnlinePaymentChannel.EFT;
    p.onlinePaymentReference = reference;
    return p;
  }
}
