package com.productstore.platform.constants;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Granular Support console capabilities (PLATFORM_ADMIN always has all). */
public enum SupportPermission {
  MANAGE_SUBSCRIPTIONS,
  MANAGE_PLANS,
  MANAGE_MERCHANTS,
  USE_SHADOW,
  MANAGE_TICKETS,
  MANAGE_FEATURES,
  VIEW_AUDIT,
  MANAGE_STAFF,
  VIEW_OPS;

  public static final List<SupportPermission> ALL = List.of(values());

  public static final List<SupportPermission> DEFAULT_SUPPORT =
      List.of(
          MANAGE_SUBSCRIPTIONS,
          MANAGE_MERCHANTS,
          USE_SHADOW,
          MANAGE_TICKETS,
          VIEW_OPS);

  public static Set<SupportPermission> parseCsv(String raw) {
    Set<SupportPermission> out = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) return out;
    for (String part : raw.split(",")) {
      String p = part.trim();
      if (p.isEmpty()) continue;
      try {
        out.add(SupportPermission.valueOf(p.toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // skip unknown
      }
    }
    return out;
  }

  public static String toCsv(Iterable<SupportPermission> perms) {
    StringBuilder sb = new StringBuilder();
    for (SupportPermission p : perms) {
      if (sb.length() > 0) sb.append(',');
      sb.append(p.name());
    }
    return sb.toString();
  }

  public static List<String> names(Iterable<SupportPermission> perms) {
    return Arrays.stream(SupportPermission.values())
            .filter(
                p -> {
                  for (SupportPermission x : perms) {
                    if (x == p) return true;
                  }
                  return false;
                })
            .map(Enum::name)
            .collect(Collectors.toList());
  }
}
