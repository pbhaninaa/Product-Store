package com.productstore.platform.services;

import java.security.SecureRandom;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;

/**
 * The 6-digit handover code every order and booking gets when it is placed, whatever the payment method.
 * The customer shows it to staff: staff enter it to start a salon service and to hand over an order on
 * collection or delivery. For pay-in-store rows the same entry also records the cash payment. Checked through
 * {@link CashCodeGuard}, which limits wrong guesses.
 */
public final class HandoverCodes {
  private static final SecureRandom RANDOM = new SecureRandom();

  private HandoverCodes() {}

  public static String generate() {
    return String.valueOf(100_000 + RANDOM.nextInt(900_000));
  }

  public static boolean isSet(String code) {
    return code != null && !code.isBlank();
  }

  /**
   * The code to show the customer while it can still be used: the order is neither cancelled nor handed over.
   * Null otherwise.
   */
  public static String forCustomer(OrderEntity o) {
    if (o == null || o.cancelledAt != null || !isSet(o.handoverCode)) return null;
    if (o.status == OrderEntity.OrderStatus.pending_payment) return o.handoverCode;
    if (o.status == OrderEntity.OrderStatus.paid
        && OrderEntity.effectiveFulfillment(o) != OrderEntity.FulfillmentStatus.completed) {
      return o.handoverCode;
    }
    return null;
  }

  /** The code to show the customer until the service has started. Null otherwise. */
  public static String forCustomer(SalonBookingEntity b) {
    if (b == null || !isSet(b.handoverCode)) return null;
    return b.status == SalonBookingEntity.Status.pending || b.status == SalonBookingEntity.Status.confirmed
        ? b.handoverCode
        : null;
  }

  /** The pay-in-store code under its old name: only for an unpaid cash order (older clients read it). */
  public static String unpaidCashCode(OrderEntity o) {
    boolean unpaidCash =
        o != null
            && o.status == OrderEntity.OrderStatus.pending_payment
            && o.cancelledAt == null
            && o.paymentMethod == OrderEntity.PaymentMethod.cash_store;
    return unpaidCash && isSet(o.handoverCode) ? o.handoverCode : null;
  }

  /** The pay-in-store code under its old name: only for an unpaid cash booking (older clients read it). */
  public static String unpaidCashCode(SalonBookingEntity b) {
    boolean unpaidCash =
        b != null
            && b.status == SalonBookingEntity.Status.pending
            && b.clientPaymentMethod == SalonBookingEntity.ClientPaymentMethod.cash_store;
    return unpaidCash && isSet(b.handoverCode) ? b.handoverCode : null;
  }
}
