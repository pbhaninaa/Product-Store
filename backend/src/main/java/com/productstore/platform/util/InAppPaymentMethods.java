package com.productstore.platform.util;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;

/** In-app hosted checkout rails: PayFast (current) and leftover Peach rows. */
public final class InAppPaymentMethods {
  private InAppPaymentMethods() {}

  public static boolean isInApp(OrderEntity.PaymentMethod method) {
    return method == OrderEntity.PaymentMethod.peach || method == OrderEntity.PaymentMethod.payfast;
  }

  public static boolean isInApp(SalonBookingEntity.ClientPaymentMethod method) {
    return method == SalonBookingEntity.ClientPaymentMethod.peach
        || method == SalonBookingEntity.ClientPaymentMethod.payfast;
  }

  public static OrderEntity.PaymentMethod normalizeOrderMethod(String raw) {
    if (raw == null) return null;
    String v = raw.trim().toLowerCase();
    if ("peach".equals(v) || "payfast".equals(v)) {
      return OrderEntity.PaymentMethod.payfast;
    }
    try {
      return OrderEntity.PaymentMethod.valueOf(v);
    } catch (Exception e) {
      return null;
    }
  }
}
