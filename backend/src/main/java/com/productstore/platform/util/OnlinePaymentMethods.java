package com.productstore.platform.util;

import java.util.Locale;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;

/** Helpers for the provider neutral {@code online} payment method on orders and bookings. */
public final class OnlinePaymentMethods {
  private OnlinePaymentMethods() {}

  public static boolean isOnline(OrderEntity.PaymentMethod method) {
    return method == OrderEntity.PaymentMethod.online;
  }

  public static boolean isOnline(SalonBookingEntity.ClientPaymentMethod method) {
    return method == SalonBookingEntity.ClientPaymentMethod.online;
  }

  /** Parses a request {@code paymentMethod} ({@code cash_store}, {@code eft} or {@code online}); null if unknown. */
  public static OrderEntity.PaymentMethod parseOrderMethod(String raw) {
    if (raw == null) return null;
    try {
      return OrderEntity.PaymentMethod.valueOf(raw.trim().toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
