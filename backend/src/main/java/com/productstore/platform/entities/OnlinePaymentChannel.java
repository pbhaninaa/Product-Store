package com.productstore.platform.entities;

import java.util.Locale;

/** Customer-selected rail for an online payment: card or Instant EFT. */
public enum OnlinePaymentChannel {
  CARD,
  EFT;

  /** Parses the {@code onlinePaymentChannel} request value; error codes are part of the API contract. */
  public static OnlinePaymentChannel fromRequest(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("online_payment_channel_required");
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_online_payment_channel");
    }
  }
}
