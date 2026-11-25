package com.productstore.platform.config;

import com.productstore.platform.entities.ShopSettingsEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform-wide override on top of each shop's own payment toggles.
 *
 * <p>When {@code app.payments.customer-cash-only=true} (env {@code CUSTOMER_PAYMENTS_CASH_ONLY}),
 * online payment is hidden and rejected for every shop, and cash is always offered. Used for demo deployments
 * where no real money should move.
 *
 * <p>Manual EFT (bank transfer with proof upload) was removed for customers on 2026-11-23: {@link #acceptEft}
 * is always false, whatever the shop's legacy {@code accept_customer_eft} column says.
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

  public static boolean acceptOnline(ShopSettingsEntity s) {
    if (cashOnly) return false;
    return s == null || s.acceptCustomerOnline == null || Boolean.TRUE.equals(s.acceptCustomerOnline);
  }

  /** Manual EFT is no longer offered to customers. Kept as the one gate checkout and booking ask. */
  public static boolean acceptEft(ShopSettingsEntity s) {
    return false;
  }

  public static boolean acceptCash(ShopSettingsEntity s) {
    if (cashOnly) return true;
    return s == null || s.acceptCustomerCash == null || Boolean.TRUE.equals(s.acceptCustomerCash);
  }
}
