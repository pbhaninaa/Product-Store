package com.productstore.platform.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;

import org.junit.jupiter.api.Test;

class ClientHistoryCashCodeTest {

  private static OrderEntity unpaidCashOrder() {
    OrderEntity o = new OrderEntity();
    o.status = OrderEntity.OrderStatus.pending_payment;
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.cashPaymentCode = "AB12CD";
    return o;
  }

  @Test
  void showsCodeWhileOrderAwaitsCashPayment() {
    assertThat(ClientHistoryController.unpaidOrderCashCode(unpaidCashOrder())).isEqualTo("AB12CD");
  }

  @Test
  void hidesCodeOnceOrderIsPaid() {
    OrderEntity o = unpaidCashOrder();
    o.status = OrderEntity.OrderStatus.paid;
    assertThat(ClientHistoryController.unpaidOrderCashCode(o)).isNull();
  }

  @Test
  void hidesCodeWhenOrderIsCancelled() {
    OrderEntity o = unpaidCashOrder();
    o.cancelledAt = Instant.now();
    assertThat(ClientHistoryController.unpaidOrderCashCode(o)).isNull();
  }

  @Test
  void hidesCodeForNonCashOrders() {
    OrderEntity o = unpaidCashOrder();
    o.paymentMethod = OrderEntity.PaymentMethod.eft;
    assertThat(ClientHistoryController.unpaidOrderCashCode(o)).isNull();
  }

  @Test
  void hidesBlankOrderCode() {
    OrderEntity o = unpaidCashOrder();
    o.cashPaymentCode = "  ";
    assertThat(ClientHistoryController.unpaidOrderCashCode(o)).isNull();
  }

  @Test
  void bookingCodeOnlyWhilePendingCash() {
    SalonBookingEntity b = new SalonBookingEntity();
    b.status = SalonBookingEntity.Status.pending;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    b.cashPaymentCode = "XY34ZW";
    assertThat(ClientHistoryController.unpaidBookingCashCode(b)).isEqualTo("XY34ZW");

    b.status = SalonBookingEntity.Status.confirmed;
    assertThat(ClientHistoryController.unpaidBookingCashCode(b)).isNull();
  }
}
