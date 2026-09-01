package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.config.PayFastProperties;
import com.productstore.platform.config.PeachProperties;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.ShopSettingsDefaults;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}")
public class PublicShopSettingsController {
  private final TenantAccessService tenantAccess;
  private final ShopSettingsRepository settings;
  private final SalonAccessService salonAccess;
  private final PeachProperties peachProperties;
  private final PayFastProperties payFastProperties;

  public PublicShopSettingsController(
      TenantAccessService tenantAccess,
      ShopSettingsRepository settings,
      SalonAccessService salonAccess,
      PeachProperties peachProperties,
      PayFastProperties payFastProperties) {
    this.tenantAccess = tenantAccess;
    this.settings = settings;
    this.salonAccess = salonAccess;
    this.peachProperties = peachProperties;
    this.payFastProperties = payFastProperties;
  }

  @GetMapping("/shop-settings")
  public Map<String, Object> shopSettings(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    ShopSettingsEntity s =
        settings
            .findByTenantId(tenant.id())
            .orElseGet(() -> settings.save(ShopSettingsDefaults.newRowForTenant(tenant.id())));

    String shopType = SalonAccessService.normalizedShopType(s.shopType);
    boolean salonEnabled = salonAccess.isSalonShop(tenant.id());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("deliveryFeeMode", s.deliveryFeeMode);
    out.put("deliveryFeeZar", s.deliveryFeeFlatZar.toPlainString());
    out.put("deliveryFeePerKmZar", s.deliveryFeePerKmZar.toPlainString());
    out.put("storeLat", s.storeLat);
    out.put("storeLng", s.storeLng);
    out.put("eftBankInstructions", s.eftBankInstructions);
    out.put("bankName", s.bankName);
    out.put("bankAccountHolder", s.bankAccountHolder);
    out.put("bankAccountNumber", s.bankAccountNumber);
    out.put("bankBranchCode", s.bankBranchCode);
    out.put("storeName", s.storeName);
    out.put("storeLogoUrl", publicLogoUrl(merchantSlug, s));
    out.put("storeHeroUrl", publicHeroUrl(merchantSlug, s));
    out.put("shopType", shopType);
    out.put("salonEnabled", salonEnabled);
    out.put("contactEmail", s.contactEmail == null ? "" : s.contactEmail);
    out.put("contactPhone", s.contactPhone == null ? "" : s.contactPhone);
    out.put("contactAddress", s.contactAddress == null ? "" : s.contactAddress);
    out.put("contactNotes", s.contactNotes == null ? "" : s.contactNotes);
    out.put("openingHoursJson", s.openingHoursJson == null || s.openingHoursJson.isBlank() ? "[]" : s.openingHoursJson);
    out.put(
        "acceptCustomerPeach",
        s.acceptCustomerPeach == null ? Boolean.TRUE : Boolean.TRUE.equals(s.acceptCustomerPeach));
    out.put(
        "acceptCustomerEft",
        s.acceptCustomerEft == null ? Boolean.TRUE : Boolean.TRUE.equals(s.acceptCustomerEft));
    out.put(
        "acceptCustomerCash",
        s.acceptCustomerCash == null ? Boolean.TRUE : Boolean.TRUE.equals(s.acceptCustomerCash));
    boolean inAppConfigured = payFastProperties.isConfigured() || peachProperties.isConfigured();
    out.put("peachConfigured", inAppConfigured);
    out.put("payfastConfigured", payFastProperties.isConfigured());
    return out;
  }

  @GetMapping("/branding/logo")
  public ResponseEntity<byte[]> brandingLogo(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    ShopSettingsEntity s =
        settings
            .findByTenantId(tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("settings_not_found"));
    if (s.storeLogoData == null || s.storeLogoData.length == 0) {
      return ResponseEntity.notFound().build();
    }
    String ct =
        s.storeLogoContentType == null || s.storeLogoContentType.isBlank()
            ? "image/jpeg"
            : s.storeLogoContentType;
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct)).body(s.storeLogoData);
  }

  @GetMapping("/branding/hero")
  public ResponseEntity<byte[]> brandingHero(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    ShopSettingsEntity s =
        settings
            .findByTenantId(tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("settings_not_found"));
    if (s.storeHeroData == null || s.storeHeroData.length == 0) {
      return ResponseEntity.notFound().build();
    }
    String ct =
        s.storeHeroContentType == null || s.storeHeroContentType.isBlank()
            ? "image/jpeg"
            : s.storeHeroContentType;
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct)).body(s.storeHeroData);
  }

  public static String publicLogoUrl(String merchantSlug, ShopSettingsEntity s) {
    if (s.storeLogoData != null && s.storeLogoData.length > 0) {
      return "/api/public/m/" + merchantSlug + "/branding/logo";
    }
    return externalBrandingUrl(s.storeLogoUrl);
  }

  static String publicHeroUrl(String merchantSlug, ShopSettingsEntity s) {
    if (s.storeHeroData != null && s.storeHeroData.length > 0) {
      return "/api/public/m/" + merchantSlug + "/branding/hero";
    }
    return externalBrandingUrl(s.storeHeroUrl);
  }

  private static String externalBrandingUrl(String raw) {
    String u = raw == null ? "" : raw.trim();
    if (u.isEmpty()) return "";
    if (u.startsWith("/api/m/") && u.contains("/branding/")) return "";
    return u;
  }

}

