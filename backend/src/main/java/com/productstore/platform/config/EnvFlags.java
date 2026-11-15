package com.productstore.platform.config;

/** Parses Railway/env flags where a key can be present but blank. */
public final class EnvFlags {
  private EnvFlags() {}

  public static boolean parse(String raw, boolean defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    String value = raw.trim();
    if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
      return false;
    }
    return defaultValue;
  }
}
