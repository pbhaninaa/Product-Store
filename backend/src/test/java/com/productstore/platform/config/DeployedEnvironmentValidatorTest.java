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
}
