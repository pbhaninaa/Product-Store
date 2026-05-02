package com.productstore.platform.services;

/** Normalizes URL-safe tenant slugs (same rules as public merchant signup). */
public final class TenantSlugUtil {
  private TenantSlugUtil() {}

  public static String normalize(String raw) {
    String s = raw == null ? "" : raw.trim().toLowerCase();
    s = s.replaceAll("[^a-z0-9-]", "-");
    s = s.replaceAll("-{2,}", "-");
    s = s.replaceAll("^-+", "").replaceAll("-+$", "");
    if (s.length() < 2) throw new IllegalArgumentException("invalid_slug");
    if (s.length() > 48) throw new IllegalArgumentException("invalid_slug");
    return s;
  }
}
