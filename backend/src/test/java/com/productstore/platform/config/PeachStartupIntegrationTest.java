package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.productstore.platform.controllers.PlatformApplication;

/**
 * Ensures Peach stays off by default and schema migration does not break H2/create-drop tests.
 */
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
class PeachStartupIntegrationTest {
  @Autowired PeachProperties peachProperties;
  @Autowired PeachSchemaMigration peachSchemaMigration;

  @Test
  void peachDisabledAndUnconfiguredByDefault() {
    assertFalse(peachProperties.isEnabled());
    assertFalse(peachProperties.isConfigured());
  }

  @Test
  void schemaMigrationIsIdempotentOnNonMysqlTestProfile() {
    assertDoesNotThrow(() -> peachSchemaMigration.run(null));
    assertDoesNotThrow(() -> peachSchemaMigration.run(null));
  }
}
