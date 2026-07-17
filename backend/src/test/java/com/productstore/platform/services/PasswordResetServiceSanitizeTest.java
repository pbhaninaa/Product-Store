package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PasswordResetServiceSanitizeTest {

  @Test
  void stripsDuplicatedHttpsFromFrontendBaseUrl() {
    assertEquals(
        "https://www.example.com",
        PasswordResetService.sanitizeFrontendBaseUrl("https://https://www.example.com/"));
  }
}
