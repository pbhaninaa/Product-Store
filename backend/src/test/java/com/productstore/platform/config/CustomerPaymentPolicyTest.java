package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.productstore.platform.entities.ShopSettingsEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CustomerPaymentPolicyTest {

  @AfterEach
  void reset() {
    CustomerPaymentPolicy.setCashOnly(false);
  }

  @Test
  void followsShopSettingsWhenOff() {
    new CustomerPaymentPolicy("false");
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.acceptCustomerPeach = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = false;
    assertTrue(CustomerPaymentPolicy.acceptPeach(s));
    assertTrue(CustomerPaymentPolicy.acceptEft(s));
    assertFalse(CustomerPaymentPolicy.acceptCash(s));
  }

  @Test
  void cashOnlyOverridesShopSettings() {
    new CustomerPaymentPolicy(" TRUE ");
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.acceptCustomerPeach = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = false;
    assertFalse(CustomerPaymentPolicy.acceptPeach(s));
    assertFalse(CustomerPaymentPolicy.acceptEft(s));
    assertTrue(CustomerPaymentPolicy.acceptCash(s));
    assertTrue(CustomerPaymentPolicy.acceptCash(null));
  }

  @Test
  void blankValueMeansOff() {
    new CustomerPaymentPolicy("");
    assertFalse(CustomerPaymentPolicy.isCashOnly());
  }
}
