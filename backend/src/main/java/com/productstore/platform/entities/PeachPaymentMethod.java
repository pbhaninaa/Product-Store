package com.productstore.platform.entities;

import java.util.Locale;

/** Customer-selected payment rail within Peach Hosted Checkout. */
public enum PeachPaymentMethod {
  CARD("CARD"),
  EFT("PAYBYBANK");

  private final String hostedCheckoutMethod;

  PeachPaymentMethod(String hostedCheckoutMethod) {
    this.hostedCheckoutMethod = hostedCheckoutMethod;
  }

  public String hostedCheckoutMethod() {
    return hostedCheckoutMethod;
  }

  public static PeachPaymentMethod fromRequest(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("peach_payment_method_required");
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_peach_payment_method");
    }
  }
}
