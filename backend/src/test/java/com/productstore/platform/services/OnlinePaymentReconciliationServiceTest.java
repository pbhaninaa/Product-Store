package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.services.PaystackPaymentService.VerifyResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class OnlinePaymentReconciliationServiceTest {
  private final Instant now = Instant.parse("2026-11-23T10:00:00Z");
  private OrderRepository orders;
  private SalonBookingRepository bookings;
  private SubscriptionOnlinePaymentRepository subscriptionPayments;
  private PaystackPaymentService paystack;
  private OnlinePaymentReconciliationService reconciliation;

  @BeforeEach
  void setUp() {
    orders = mock(OrderRepository.class);
    bookings = mock(SalonBookingRepository.class);
    subscriptionPayments = mock(SubscriptionOnlinePaymentRepository.class);
    paystack = mock(PaystackPaymentService.class);
    when(orders.findOnlineAwaitingConfirmation(any(), any())).thenReturn(List.of());
    when(bookings.findOnlineAwaitingConfirmation(any(), any())).thenReturn(List.of());
    when(subscriptionPayments.findByStatusSince(any(), any(), any())).thenReturn(List.of());
    when(paystack.verifyAndSettle(anyString(), anyString(), any())).thenReturn(VerifyResult.PENDING);
    reconciliation = new OnlinePaymentReconciliationService(orders, bookings, subscriptionPayments, paystack);
  }

  @Test
  void looksBack24HoursAndVerifiesEveryKindOldestFirst() {
    OrderEntity o = order("ORDAAAABBBBCCCCDDDD", now.minus(Duration.ofHours(3)));
    SalonBookingEntity b = booking("BKGAAAABBBBCCCCDDDD", now.minus(Duration.ofHours(5)));
    SubscriptionOnlinePaymentEntity p = subscription("SUBAAAABBBBCCCCDDDD", now.minus(Duration.ofHours(1)));
    when(orders.findOnlineAwaitingConfirmation(any(), any())).thenReturn(List.of(o));
    when(bookings.findOnlineAwaitingConfirmation(any(), any())).thenReturn(List.of(b));
    when(subscriptionPayments.findByStatusSince(eq(SubscriptionOnlinePaymentEntity.STATUS_PENDING), any(), any()))
        .thenReturn(List.of(p));
    when(paystack.verifyAndSettle("ORDAAAABBBBCCCCDDDD", "order", o.id)).thenReturn(VerifyResult.SETTLED);
    when(paystack.verifyAndSettle("BKGAAAABBBBCCCCDDDD", "booking", b.id)).thenReturn(VerifyResult.NOT_PAID);

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary).isEqualTo(new OnlinePaymentReconciliationService.Summary(3, 1, 1, 1, 0, 0));
    ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
    verify(orders).findOnlineAwaitingConfirmation(since.capture(), any());
    assertThat(since.getValue()).isEqualTo(now.minus(Duration.ofHours(24)));
    var inOrder = org.mockito.Mockito.inOrder(paystack);
    inOrder.verify(paystack).verifyAndSettle("BKGAAAABBBBCCCCDDDD", "booking", b.id);
    inOrder.verify(paystack).verifyAndSettle("ORDAAAABBBBCCCCDDDD", "order", o.id);
    inOrder.verify(paystack).verifyAndSettle("SUBAAAABBBBCCCCDDDD", "subscription", p.id);
  }

  @Test
  void otherAppsAndOldStyleReferencesAreNeverSentToPaystack() {
    when(orders.findOnlineAwaitingConfirmation(any(), any()))
        .thenReturn(
            List.of(
                order("T685312322670591", now.minusSeconds(60)),
                order("a1b2c3d4e5f6a7b8", now.minusSeconds(50)),
                order("BKGAAAABBBBCCCCDDDD", now.minusSeconds(40)))); // booking prefix on an order row

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.checked()).isZero();
    verify(paystack, never()).verifyAndSettle(anyString(), anyString(), any());
  }

  @Test
  void batchLimitIsRespected() {
    List<OrderEntity> many = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      many.add(order(String.format("ORD%016d", i), now.minusSeconds(1000 - i)));
    }
    when(orders.findOnlineAwaitingConfirmation(any(), any())).thenReturn(many);

    var summary = reconciliation.reconcile(now, 4);

    assertThat(summary.checked()).isEqualTo(4);
    verify(paystack, times(4)).verifyAndSettle(anyString(), eq("order"), any());
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(orders).findOnlineAwaitingConfirmation(any(), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(4);
  }

  @Test
  void runStopsWhenPaystackCannotBeReached() {
    when(orders.findOnlineAwaitingConfirmation(any(), any()))
        .thenReturn(List.of(order("ORD0000000000000001", now.minusSeconds(30)), order("ORD0000000000000002", now.minusSeconds(20))));
    when(paystack.verifyAndSettle(eq("ORD0000000000000001"), anyString(), any())).thenReturn(VerifyResult.ERROR);

    var summary = reconciliation.reconcile(now, 50);

    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.checked()).isEqualTo(1);
    verify(paystack, never()).verifyAndSettle(eq("ORD0000000000000002"), anyString(), any());
  }

  private static OrderEntity order(String reference, Instant createdAt) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.onlinePaymentReference = reference;
    o.createdAt = createdAt;
    return o;
  }

  private static SalonBookingEntity booking(String reference, Instant createdAt) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.onlinePaymentReference = reference;
    b.createdAt = createdAt;
    return b;
  }

  private static SubscriptionOnlinePaymentEntity subscription(String reference, Instant createdAt) {
    SubscriptionOnlinePaymentEntity p = new SubscriptionOnlinePaymentEntity();
    p.id = UUID.randomUUID();
    p.onlinePaymentReference = reference;
    p.createdAt = createdAt;
    return p;
  }
}
