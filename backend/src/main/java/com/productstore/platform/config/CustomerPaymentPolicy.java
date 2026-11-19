package com.productstore.platform.config;

import com.productstore.platform.entities.ShopSettingsEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform-wide override on top of each shop's own payment toggles.
 *
 * <p>When {@code app.payments.customer-cash-only=true} (env {@code CUSTOMER_PAYMENTS_CASH_ONLY}),
 * Manual EFT and PayFast are hidden and rejected for every shop, and cash is always offered. Used for
 * demo deployments where no real money should move.
 */
@Component
public class CustomerPaymentPolicy {

  private static volatile boolean cashOnly = false;

  public CustomerPaymentPolicy(@Value("${app.payments.customer-cash-only:false}") String raw) {
    setCashOnly(raw != null && "true".equalsIgnoreCase(raw.trim()));
  }

  static void setCashOnly(boolean value) {
    cashOnly = value;
  }

  public static boolean isCashOnly() {
    return cashOnly;
  }

  public static boolean acceptPeach(ShopSettingsEntity s) {
    if (cashOnly) return false;
    return s == null || s.acceptCustomerPeach == null || Boolean.TRUE.equals(s.acceptCustomerPeach);
  }

  public static boolean acceptEft(ShopSettingsEntity s) {
    if (cashOnly) return false;
    return s == null || s.acceptCustomerEft == null || Boolean.TRUE.equals(s.acceptCustomerEft);
  }

  public static boolean acceptCash(ShopSettingsEntity s) {
    if (cashOnly) return true;
    return s == null || s.acceptCustomerCash == null || Boolean.TRUE.equals(s.acceptCustomerCash);
  }
}
