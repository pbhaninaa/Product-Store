package com.productstore.platform.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails UAT/PROD startup when settings that silently break user-facing links are missing.
 * Without a frontend URL, password-reset emails and online payment return URLs point nowhere.
 */
@Component
@Profile({"uat", "prod"})
public class DeployedEnvironmentValidator {
  private static final Logger log = LoggerFactory.getLogger(DeployedEnvironmentValidator.class);

  private final String frontendBaseUrl;
  private final PaystackProperties paystack;
  private final boolean prod;
  private final String adminEmail;
  private final String adminPassword;

  public DeployedEnvironmentValidator(
      @Value("${app.frontend-base-url:}") String frontendBaseUrl,
      @Value("${spring.profiles.active:}") String activeProfiles,
      PaystackProperties paystack,
      @Value("${app.bootstrap.platformAdmin.email:}") String adminEmail,
      @Value("${app.bootstrap.platformAdmin.password:}") String adminPassword) {
    this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
    this.adminPassword = adminPassword == null ? "" : adminPassword;
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    this.paystack = paystack;
    this.prod = activeProfiles != null && activeProfiles.toLowerCase().contains("prod");
  }

  @PostConstruct
  void validate() {
    validateFrontendUrl(frontendBaseUrl);
    validatePlatformAdmin(adminEmail, adminPassword);
    validatePaystackKey(prod, paystack.isEnabled(), paystack.getSecretKey());
    if (!prod && paystack.isEnabled() && paystack.isLiveKey()) {
      log.warn("Paystack uses a live secret key (sk_live_) outside PROD; real money will move on this environment.");
    }
  }

  /**
   * PROD must take real payments: with Paystack enabled the secret key must be a live key. A test key would
   * show customers a checkout that never charges them, so startup fails instead.
   */
  static void validatePaystackKey(boolean prod, boolean paystackEnabled, String secretKey) {
    if (!prod || !paystackEnabled) {
      return;
    }
    if (secretKey == null || !secretKey.trim().startsWith("sk_live_")) {
      throw new IllegalStateException(
          "PAYSTACK_SECRET_KEY must be a live key (sk_live_...) on PROD while PAYSTACK_ENABLED=true.");
    }
  }

  /**
   * The platform admin must be seeded from env on deployed environments. Otherwise the first-run
   * signup would let whoever reaches the site first become platform admin.
   */
  static void validatePlatformAdmin(String email, String password) {
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      throw new IllegalStateException(
          "PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_PASSWORD must be set on UAT/PROD.");
    }
    if (password.length() < 12) {
      throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD must be at least 12 characters on UAT/PROD.");
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
