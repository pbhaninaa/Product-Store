package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.productstore.platform.controllers.PlatformApplication;

/**
 * Railway lets a variable exist with an empty value. Spring only applies a {@code ${X:default}}
 * when X is missing, so every boolean flag must tolerate blank values or the app fails to boot.
 */
@SpringBootTest(
    classes = PlatformApplication.class,
    properties = {
      "paystack.enabled=",
      "paystack.secret-key=",
      "paystack.base-url=",
      "app.whatsapp.enabled=",
      "app.bootstrap.platformAdmin.syncPassword=",
      "app.bootstrap.demoMerchant.enabled=",
      "app.bootstrap.demoClient.enabled="
    })
@ActiveProfiles("test")
class BlankEnvFlagsBootTest {
  @Autowired PaystackProperties paystackProperties;

  @Test
  void blankFlagsBootWithDefaults() {
    assertFalse(paystackProperties.isEnabled());
    assertFalse(paystackProperties.isConfigured());
    assertTrue(paystackProperties.getBaseUrl().startsWith("https://api.paystack.co"));
  }
}
