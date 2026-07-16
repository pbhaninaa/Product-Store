package com.productstore.platform.services;

import java.util.function.Predicate;

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

  /** Builds a slug candidate from a business / store display name. */
  public static String fromBusinessName(String businessName) {
    String base = businessName == null ? "" : businessName.trim().toLowerCase();
    base = base.replaceAll("[^a-z0-9]+", "-");
    base = base.replaceAll("-{2,}", "-");
    base = base.replaceAll("^-+", "").replaceAll("-+$", "");
    if (base.length() < 2) throw new IllegalArgumentException("invalid_business_name");
    if (base.length() > 48) base = base.substring(0, 48).replaceAll("-+$", "");
    if (base.length() < 2) throw new IllegalArgumentException("invalid_business_name");
    return normalize(base);
  }

  /**
   * Returns {@code base} if free, otherwise {@code base-2}, {@code base-3}, … truncated to stay
   * within 48 characters.
   */
  public static String allocateUnique(String baseSlug, Predicate<String> taken) {
    String base = normalize(baseSlug);
    if (!taken.test(base)) return base;
    for (int i = 2; i < 10000; i++) {
      String suffix = "-" + i;
      int maxBase = 48 - suffix.length();
      String stem = base.length() <= maxBase ? base : base.substring(0, maxBase).replaceAll("-+$", "");
      if (stem.length() < 2) stem = "shop";
      String candidate = stem + suffix;
      if (!taken.test(candidate)) return candidate;
    }
    throw new IllegalStateException("slug_allocation_exhausted");
  }
}
