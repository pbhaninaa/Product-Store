package com.productstore.platform.services;

import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailFromAddressResolver {
  private final String domain;
  private final String fallbackAddress;

  public EmailFromAddressResolver(
      @Value("${app.email.domain:}") String domain,
      @Value("${app.email.from:no-reply@localhost}") String fallbackAddress) {
    this.domain = normalizeDomain(domain);
    this.fallbackAddress = fallbackAddress == null ? "" : fallbackAddress.trim();
  }

  public String resolve(EmailPurpose purpose) {
    Objects.requireNonNull(purpose, "purpose");
    if (domain.isBlank()) {
      return fallbackAddress;
    }
    return purpose.localPart() + "@" + domain;
  }

  private static String normalizeDomain(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    while (normalized.startsWith("@")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }
}
