package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class WebConfigCorsTest {

  private static CorsConfiguration cors(MockEnvironment env) {
    UrlBasedCorsConfigurationSource source =
        (UrlBasedCorsConfigurationSource) new WebConfig().corsConfigurationSource(env);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    return source.getCorsConfiguration(request);
  }

  @Test
  void deployedProfileWithoutOriginsRefusesBrowsersInsteadOfAllowingAll() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod");
    CorsConfiguration cfg = cors(env);
    assertTrue(cfg.getAllowedOrigins() == null || cfg.getAllowedOrigins().isEmpty());
    assertTrue(cfg.getAllowedOriginPatterns() == null || cfg.getAllowedOriginPatterns().isEmpty());
  }

  @Test
  void deployedProfileUsesConfiguredOriginsWithoutTrailingSlash() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("uat");
    env.setProperty("UAT_CORS_ORIGINS", "https://uat.example.com/");
    assertEquals(List.of("https://uat.example.com"), cors(env).getAllowedOrigins());
  }

  @Test
  void sitAllowsAnyOrigin() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("sit");
    assertEquals(List.of("*"), cors(env).getAllowedOriginPatterns());
  }
}
