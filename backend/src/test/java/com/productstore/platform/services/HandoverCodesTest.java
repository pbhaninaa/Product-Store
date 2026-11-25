package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;

import org.junit.jupiter.api.Test;

/** Which code the customer sees, and when (track page, history, confirmation). */
class HandoverCodesTest {

  private static OrderEntity order(OrderEntity.PaymentMethod method, OrderEntity.OrderStatus status) {
    OrderEntity o = new OrderEntity();
    o.status = status;
    o.paymentMethod = method;
    o.handoverCode = "123456";
    return o;
  }

  private static SalonBookingEntity booking(
      SalonBookingEntity.ClientPaymentMethod method, SalonBookingEntity.Status status) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.status = status;
    b.clientPaymentMethod = method;
    b.handoverCode = "654321";
    return b;
  }

  @Test
  void generatesSixDigitCodes() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String c = HandoverCodes.generate();
      assertThat(c).matches("[1-9][0-9]{5}");
      seen.add(c);
    }
    assertThat(seen.size()).isGreaterThan(150);
  }

  @Test
  void orderCodeShownForEveryPaymentMethodUntilHandover() {
    for (OrderEntity.PaymentMethod pm : OrderEntity.PaymentMethod.values()) {
      assertThat(HandoverCodes.forCustomer(order(pm, OrderEntity.OrderStatus.pending_payment))).isEqualTo("123456");
      OrderEntity paid = order(pm, OrderEntity.OrderStatus.paid);
      paid.fulfillmentStatus = null; // treated as processing
      assertThat(HandoverCodes.forCustomer(paid)).isEqualTo("123456");
      paid.fulfillmentStatus = OrderEntity.FulfillmentStatus.ready;
      assertThat(HandoverCodes.forCustomer(paid)).isEqualTo("123456");
      paid.fulfillmentStatus = OrderEntity.FulfillmentStatus.completed;
      assertThat(HandoverCodes.forCustomer(paid)).isNull();
    }
  }

  @Test
  void orderCodeHiddenWhenCancelledOrBlank() {
    OrderEntity cancelled = order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.cancelled);
    assertThat(HandoverCodes.forCustomer(cancelled)).isNull();
    OrderEntity expired = order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment);
    expired.cancelledAt = Instant.now();
    assertThat(HandoverCodes.forCustomer(expired)).isNull();
    OrderEntity blank = order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.paid);
    blank.handoverCode = "  ";
    assertThat(HandoverCodes.forCustomer(blank)).isNull();
    assertThat(HandoverCodes.forCustomer((OrderEntity) null)).isNull();
  }

  @Test
  void legacyCashCodeOnlyWhileACashOrderIsUnpaid() {
    OrderEntity o = order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment);
    assertThat(HandoverCodes.unpaidCashCode(o)).isEqualTo("123456");
    o.status = OrderEntity.OrderStatus.paid;
    assertThat(HandoverCodes.unpaidCashCode(o)).isNull();
    OrderEntity online = order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.pending_payment);
    assertThat(HandoverCodes.unpaidCashCode(online)).isNull();
    OrderEntity cancelled = order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment);
    cancelled.cancelledAt = Instant.now();
    assertThat(HandoverCodes.unpaidCashCode(cancelled)).isNull();
  }

  @Test
  void bookingCodeShownForEveryPaymentMethodUntilTheServiceStarts() {
    for (SalonBookingEntity.ClientPaymentMethod pm : SalonBookingEntity.ClientPaymentMethod.values()) {
      assertThat(HandoverCodes.forCustomer(booking(pm, SalonBookingEntity.Status.pending))).isEqualTo("654321");
      assertThat(HandoverCodes.forCustomer(booking(pm, SalonBookingEntity.Status.confirmed))).isEqualTo("654321");
      assertThat(HandoverCodes.forCustomer(booking(pm, SalonBookingEntity.Status.in_progress))).isNull();
      assertThat(HandoverCodes.forCustomer(booking(pm, SalonBookingEntity.Status.completed))).isNull();
      assertThat(HandoverCodes.forCustomer(booking(pm, SalonBookingEntity.Status.cancelled))).isNull();
    }
  }

  @Test
  void legacyCashCodeOnlyWhileACashBookingIsPending() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.cash_store, SalonBookingEntity.Status.pending);
    assertThat(HandoverCodes.unpaidCashCode(b)).isEqualTo("654321");
    b.status = SalonBookingEntity.Status.confirmed;
    assertThat(HandoverCodes.unpaidCashCode(b)).isNull();
    SalonBookingEntity online =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);
    assertThat(HandoverCodes.unpaidCashCode(online)).isNull();
  }
}
