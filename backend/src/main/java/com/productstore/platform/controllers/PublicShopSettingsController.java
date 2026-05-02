package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.ShopSettingsDefaults;
import com.productstore.platform.services.TenantAccessService;

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

  public PublicShopSettingsController(
      TenantAccessService tenantAccess, ShopSettingsRepository settings, SalonAccessService salonAccess) {
    this.tenantAccess = tenantAccess;
    this.settings = settings;
    this.salonAccess = salonAccess;
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
    out.put("storeLogoUrl", s.storeLogoUrl);
    out.put("storeHeroUrl", s.storeHeroUrl);
    out.put("shopType", shopType);
    out.put("salonEnabled", salonEnabled);
    out.put("contactEmail", s.contactEmail == null ? "" : s.contactEmail);
    out.put("contactPhone", s.contactPhone == null ? "" : s.contactPhone);
    out.put("contactAddress", s.contactAddress == null ? "" : s.contactAddress);
    out.put("contactNotes", s.contactNotes == null ? "" : s.contactNotes);
    out.put("openingHoursJson", s.openingHoursJson == null || s.openingHoursJson.isBlank() ? "[]" : s.openingHoursJson);
    out.put(
        "acceptCustomerEft",
        s.acceptCustomerEft == null ? Boolean.TRUE : Boolean.TRUE.equals(s.acceptCustomerEft));
    out.put(
        "acceptCustomerCash",
        s.acceptCustomerCash == null ? Boolean.TRUE : Boolean.TRUE.equals(s.acceptCustomerCash));
    return out;
  }

}

