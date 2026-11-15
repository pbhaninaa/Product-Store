package com.productstore.platform.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails UAT/PROD startup when settings that silently break user-facing links are missing.
 * Without a frontend URL, password-reset emails and PayFast/Peach return URLs point nowhere.
 */
@Component
@Profile({"uat", "prod"})
public class DeployedEnvironmentValidator {
  private static final Logger log = LoggerFactory.getLogger(DeployedEnvironmentValidator.class);

  private final String frontendBaseUrl;
  private final PayFastProperties payFast;
  private final boolean prod;

  public DeployedEnvironmentValidator(
      @Value("${app.frontend-base-url:}") String frontendBaseUrl,
      @Value("${spring.profiles.active:}") String activeProfiles,
      PayFastProperties payFast) {
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    this.payFast = payFast;
    this.prod = activeProfiles != null && activeProfiles.toLowerCase().contains("prod");
  }

  @PostConstruct
  void validate() {
    validateFrontendUrl(frontendBaseUrl);
    if (prod && payFast.isEnabled() && payFast.isSandbox()) {
      log.warn("PayFast is enabled in PROD but PAYFAST_SANDBOX=true; real payments will not settle.");
    }
  }

  static void validateFrontendUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "PUBLIC_APP_BASE_URL is not set. Set it to this environment's frontend URL"
              + " (e.g. https://your-app.vercel.app) so password-reset and payment return links work.");
    }
    if (!url.startsWith("https://")) {
      throw new IllegalStateException(
          "PUBLIC_APP_BASE_URL must be an https:// URL on UAT/PROD, got: " + url);
    }
  }
}
