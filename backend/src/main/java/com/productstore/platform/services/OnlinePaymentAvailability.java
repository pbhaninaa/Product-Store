package com.productstore.platform.services;

import com.productstore.platform.config.PaystackProperties;

import org.springframework.stereotype.Component;

/**
 * Whether new online payments may start: Paystack configured and not switched off by the platform
 * ({@code PAYMENTS} and {@code PAYSTACK} features). A small bean with no service dependencies, so order,
 * booking and subscription services can ask without depending on each other.
 */
@Component
public class OnlinePaymentAvailability {
  private final PaystackProperties properties;
  private final PlatformFeatureService platformFeatures;

  public OnlinePaymentAvailability(PaystackProperties properties, PlatformFeatureService platformFeatures) {
    this.properties = properties;
    this.platformFeatures = platformFeatures;
  }

  public boolean isAvailable() {
    return properties.isConfigured()
        && platformFeatures.isEnabled(PlatformFeatureService.PAYMENTS)
        && platformFeatures.isEnabled(PlatformFeatureService.PAYSTACK);
  }

  /** @throws IllegalArgumentException {@code online_payment_unavailable} */
  public void requireAvailable() {
    if (!isAvailable()) {
      throw new IllegalArgumentException("online_payment_unavailable");
    }
  }
}
