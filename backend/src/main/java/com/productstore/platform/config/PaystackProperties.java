package com.productstore.platform.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Paystack settings: {@code PAYSTACK_ENABLED}, {@code PAYSTACK_SECRET_KEY} (also signs webhooks) and
 * {@code PAYSTACK_BASE_URL}. The secret key is never logged.
 */
@Component
public class PaystackProperties {
  static final String DEFAULT_BASE_URL = "https://api.paystack.co";

  private static final Logger log = LoggerFactory.getLogger(PaystackProperties.class);

  @Value("${paystack.enabled:false}")
  private String enabled;

  @Value("${paystack.secret-key:}")
  private String secretKey;

  @Value("${paystack.base-url:" + DEFAULT_BASE_URL + "}")
  private String baseUrl;

  /** How long a Paystack verify answer is reused for the same reference (status polling, reconciliation). */
  @Value("${paystack.verify-cache-ttl:PT10S}")
  private Duration verifyCacheTtl = Duration.ofSeconds(10);

  @Autowired(required = false)
  private Environment environment;

  public boolean isEnabled() {
    return EnvFlags.parse(enabled, false);
  }

  /** True when online payment may be offered: enabled, a secret key and a usable base URL. */
  public boolean isConfigured() {
    return isEnabled() && hasSecretKey() && !getBaseUrl().isEmpty();
  }

  /**
   * True when webhooks can be verified. Kept separate from {@link #isConfigured()} so payments already in
   * flight still settle while new online checkouts are switched off.
   */
  public boolean hasSecretKey() {
    return !getSecretKey().isEmpty();
  }

  /** Paystack test keys start with {@code sk_test_}; live keys with {@code sk_live_}. */
  public boolean isTestKey() {
    return getSecretKey().startsWith("sk_test_");
  }

  public boolean isLiveKey() {
    return getSecretKey().startsWith("sk_live_");
  }

  /** True on the {@code prod} profile, where only live Paystack transactions may settle anything. */
  public boolean isProdProfile() {
    return environment != null && environment.acceptsProfiles(Profiles.of("prod"));
  }

  public Duration getVerifyCacheTtl() {
    return verifyCacheTtl == null || verifyCacheTtl.isNegative() ? Duration.ZERO : verifyCacheTtl;
  }

  public String getSecretKey() {
    return secretKey != null ? secretKey.trim() : "";
  }

  /** Normalized base URL without a trailing slash, or empty when it is not a safe URL. */
  public String getBaseUrl() {
    return normalizeBaseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl);
  }

  @PostConstruct
  void logConfigurationStatus() {
    if (!isEnabled()) {
      log.info("Paystack is disabled (set PAYSTACK_ENABLED=true and PAYSTACK_SECRET_KEY to offer online payment).");
    } else if (isConfigured()) {
      log.info("Paystack is configured ({} key).", isLiveKey() ? "live" : "test");
      if (isLiveKey() && !isProdProfile()) {
        log.warn("A live Paystack key (sk_live_) is set outside PROD; real money will move on this environment.");
      }
    } else if (!hasSecretKey()) {
      log.error("Paystack is enabled but PAYSTACK_SECRET_KEY is missing; online payment is unavailable.");
    } else {
      log.error("Paystack is enabled but PAYSTACK_BASE_URL is not a valid https URL; online payment is unavailable.");
    }
  }

  /**
   * Only {@code https} is accepted, so the secret key never travels in clear text. Plain {@code http}
   * is allowed for {@code localhost} only (local stubs in tests).
   */
  static String normalizeBaseUrl(String raw) {
    String value = raw == null ? "" : raw.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    if (value.isEmpty()) {
      return "";
    }
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      return "";
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    if (host.isEmpty()) {
      return "";
    }
    boolean local = host.equals("localhost") || host.equals("127.0.0.1");
    if (scheme.equals("https") || (scheme.equals("http") && local)) {
      return value;
    }
    return "";
  }
}
