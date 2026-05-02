package com.productstore.platform.services.multitenancy;

import java.util.Optional;

public final class TenantContext {
  private static final ThreadLocal<String> CURRENT_TENANT_SLUG = new ThreadLocal<>();

  private TenantContext() {}

  public static Optional<String> getTenantSlug() {
    return Optional.ofNullable(CURRENT_TENANT_SLUG.get());
  }

  public static void setTenantSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      CURRENT_TENANT_SLUG.remove();
      return;
    }
    CURRENT_TENANT_SLUG.set(slug.trim());
  }

  public static void clear() {
    CURRENT_TENANT_SLUG.remove();
  }
}

