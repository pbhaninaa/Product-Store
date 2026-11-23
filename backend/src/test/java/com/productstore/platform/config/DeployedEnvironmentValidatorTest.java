package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeployedEnvironmentValidatorTest {
  @Test
  void blankFrontendUrlFailsStartup() {
    assertThrows(IllegalStateException.class, () -> DeployedEnvironmentValidator.validateFrontendUrl(""));
    assertThrows(IllegalStateException.class, () -> DeployedEnvironmentValidator.validateFrontendUrl(null));
  }

  @Test
  void plainHttpFrontendUrlFailsStartup() {
    assertThrows(
        IllegalStateException.class,
        () -> DeployedEnvironmentValidator.validateFrontendUrl("http://localhost:8085"));
  }

  @Test
  void httpsFrontendUrlIsAccepted() {
    assertDoesNotThrow(
        () -> DeployedEnvironmentValidator.validateFrontendUrl("https://shop.example.com"));
  }

  @Test
  void prodWithPaystackEnabledNeedsALiveKey() {
    assertThrows(
        IllegalStateException.class,
        () -> DeployedEnvironmentValidator.validatePaystackKey(true, true, "sk_test_abc"));
    assertThrows(IllegalStateException.class, () -> DeployedEnvironmentValidator.validatePaystackKey(true, true, ""));
    assertThrows(IllegalStateException.class, () -> DeployedEnvironmentValidator.validatePaystackKey(true, true, null));
    assertDoesNotThrow(() -> DeployedEnvironmentValidator.validatePaystackKey(true, true, "sk_live_abc"));
  }

  @Test
  void testKeysAreFineOutsideProdOrWhenPaystackIsOff() {
    assertDoesNotThrow(() -> DeployedEnvironmentValidator.validatePaystackKey(false, true, "sk_test_abc"));
    assertDoesNotThrow(() -> DeployedEnvironmentValidator.validatePaystackKey(true, false, "sk_test_abc"));
  }
}
