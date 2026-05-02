package com.productstore.platform.services;

import java.util.Locale;
import java.util.UUID;

import com.productstore.platform.repositories.ShopSettingsRepository;

import org.springframework.stereotype.Service;

@Service
public final class SalonAccessService {
  public static final String SHOP_NORMAL = "normal_store";
  /** Salon + product catalogue (replaces legacy {@code salon}). */
  public static final String SHOP_SALON_AND_STORE = "salon_and_store";
  /** Appointments only; catalogue is not the primary storefront mode. */
  public static final String SHOP_SALON_ONLY = "salon_only";

  /** @deprecated Stored value before {@link #SHOP_SALON_AND_STORE}; normalized automatically. */
  public static final String SHOP_SALON_LEGACY = "salon";

  private final ShopSettingsRepository settings;

  public SalonAccessService(ShopSettingsRepository settings) {
    this.settings = settings;
  }

  /** True when tenant runs salon booking (hybrid or salon-only). */
  public boolean isSalonShop(UUID tenantId) {
    String t = normalizedShopType(tenantId);
    return SHOP_SALON_AND_STORE.equals(t) || SHOP_SALON_ONLY.equals(t);
  }

  /** True when the storefront should emphasize the product catalogue alongside salon tools. */
  public boolean isSalonCatalogHybrid(UUID tenantId) {
    return SHOP_SALON_AND_STORE.equals(normalizedShopType(tenantId));
  }

  public String normalizedShopType(UUID tenantId) {
    return settings
        .findByTenantId(tenantId)
        .map(s -> normalizedShopType(s.shopType))
        .orElse(SHOP_NORMAL);
  }

  public static String normalizedShopType(String raw) {
    if (raw == null || raw.isBlank()) {
      return SHOP_NORMAL;
    }
    String t = raw.trim().toLowerCase(Locale.ROOT);
    if (SHOP_SALON_LEGACY.equals(t)) {
      return SHOP_SALON_AND_STORE;
    }
    if (SHOP_SALON_AND_STORE.equalsIgnoreCase(t)) {
      return SHOP_SALON_AND_STORE;
    }
    if (SHOP_SALON_ONLY.equalsIgnoreCase(t)) {
      return SHOP_SALON_ONLY;
    }
    return SHOP_NORMAL;
  }

  /** Canonical value persisted on {@link com.productstore.platform.entities.ShopSettingsEntity#shopType}. */
  public static String canonicalShopTypeForSave(String raw) {
    return normalizedShopType(raw);
  }

  public void requireSalonShop(UUID tenantId) {
    if (!isSalonShop(tenantId)) {
      throw new IllegalArgumentException("salon_not_enabled");
    }
  }
}
