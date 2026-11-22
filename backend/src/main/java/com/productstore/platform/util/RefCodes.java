package com.productstore.platform.util;

import java.util.Locale;
import java.util.UUID;

/**
 * Human-friendly references for orders and bookings. People never see the UUID: they see the first
 * 8 hex characters in upper case (e.g. {@code A1B2C3D4}). The frontend uses the same rule
 * ({@code src/utils/displayLabels.js#shortRef}), so emails, receipts and the UI always agree.
 */
public final class RefCodes {
  public static final int LENGTH = 8;

  private RefCodes() {}

  public static String shortRef(UUID id) {
    if (id == null) return "";
    return id.toString().replace("-", "").substring(0, LENGTH).toUpperCase(Locale.ROOT);
  }

  public static UUID parseUuidOrNull(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return UUID.fromString(s.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Lower case with spaces, dashes, underscores and a leading '#' removed. */
  public static String normalize(String s) {
    if (s == null) return "";
    return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_#-]+", "");
  }

  /** True when {@code typed} is exactly a short reference (8 hex characters, any case/spacing). */
  public static boolean isShortRef(String typed) {
    return normalize(typed).matches("[0-9a-f]{" + LENGTH + "}");
  }

  /** True when {@code typed} is the short reference of {@code id}. */
  public static boolean isShortRefOf(String typed, UUID id) {
    return id != null && isShortRef(typed) && normalize(typed).equals(shortRef(id).toLowerCase(Locale.ROOT));
  }

  /** A bank-payment reference mentions this order/booking by its full id or its short reference. */
  public static boolean bankReferenceMentions(String declared, UUID id) {
    String n = normalize(declared);
    if (n.isEmpty() || id == null) return false;
    String compact = id.toString().replace("-", "").toLowerCase(Locale.ROOT);
    return n.contains(compact) || n.contains(shortRef(id).toLowerCase(Locale.ROOT));
  }
}
