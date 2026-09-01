package com.productstore.platform.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PayFastProperties {

  private static final Logger log = LoggerFactory.getLogger(PayFastProperties.class);

  @Value("${payfast.enabled:false}")
  private boolean enabled;

  @Value("${payfast.sandbox:true}")
  private boolean sandbox;

  @Value("${payfast.merchant-id:}")
  private String merchantId;

  @Value("${payfast.merchant-key:}")
  private String merchantKey;

  @Value("${payfast.passphrase:}")
  private String passphrase;

  @Value("${payfast.public-base-url:}")
  private String publicBaseUrl;

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isConfigured() {
    return enabled && missingConfiguration().isEmpty();
  }

  public List<String> missingConfiguration() {
    List<String> missing = new ArrayList<>();
    addIfBlank(missing, "PAYFAST_MERCHANT_ID", getMerchantId());
    addIfBlank(missing, "PAYFAST_MERCHANT_KEY", getMerchantKey());
    addIfBlank(missing, "PAYFAST_PASSPHRASE", getPassphrase());
    addIfBlank(missing, "PAYFAST_PUBLIC_BASE_URL", getPublicBaseUrl());
    return List.copyOf(missing);
  }

  public String configurationStatus() {
    if (!enabled) {
      return "disabled";
    }
    List<String> missing = missingConfiguration();
    return missing.isEmpty() ? "configured" : "missing " + String.join(", ", missing);
  }

  public boolean isSandbox() {
    return sandbox;
  }

  public String getMerchantId() {
    return merchantId != null ? merchantId.trim() : "";
  }

  public String getMerchantKey() {
    return merchantKey != null ? merchantKey.trim() : "";
  }

  public String getPassphrase() {
    return passphrase != null ? passphrase.trim() : "";
  }

  public String getPublicBaseUrl() {
    return normalizeOrigin(publicBaseUrl);
  }

  public String getProcessUrl() {
    return sandbox
        ? "https://sandbox.payfast.co.za/eng/process"
        : "https://www.payfast.co.za/eng/process";
  }

  @PostConstruct
  void logConfigurationStatus() {
    if (!enabled) {
      log.info(
          "PayFast is disabled (set PAYFAST_ENABLED=true after configuring the PayFast environment variables).");
    } else if (isConfigured()) {
      log.info("PayFast is configured for {}.", sandbox ? "sandbox" : "live");
    } else {
      log.error("PayFast is enabled but unavailable: {}.", configurationStatus());
    }
  }

  private static void addIfBlank(List<String> missing, String environmentName, String value) {
    if (value == null || value.isBlank()) {
      missing.add(environmentName);
    }
  }

  private static String normalizeOrigin(String raw) {
    String value = raw != null ? raw.trim() : "";
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
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      return "";
    }
    return value;
  }
}
