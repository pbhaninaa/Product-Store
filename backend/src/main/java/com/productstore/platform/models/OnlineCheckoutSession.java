package com.productstore.platform.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A started online payment: the browser goes to {@code paymentRedirectUrl}. {@code paymentId} is the order
 * id, booking id or subscription payment id.
 */
public record OnlineCheckoutSession(String paymentId, String paymentRedirectUrl) {

  /** {@code { paymentId, paymentRedirectUrl }} as returned by the pay endpoints. */
  public Map<String, Object> toResponse() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("paymentId", paymentId);
    out.put("paymentRedirectUrl", paymentRedirectUrl);
    return out;
  }

  /** Adds the start payment fields to an order or booking creation response. */
  public void addTo(Map<String, Object> out) {
    out.put("needsOnlinePayment", Boolean.TRUE);
    out.put("paymentId", paymentId);
    out.put("paymentRedirectUrl", paymentRedirectUrl);
  }
}
