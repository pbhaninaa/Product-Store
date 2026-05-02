package com.productstore.platform.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.productstore.platform.entities.ShopSettingsEntity;

public final class ShopSettingsDefaults {
  private ShopSettingsDefaults() {}

  public static ShopSettingsEntity newRowForTenant(UUID tenantId) {
    Instant now = Instant.now();
    ShopSettingsEntity d = new ShopSettingsEntity();
    d.id = UUID.randomUUID();
    d.tenantId = tenantId;
    d.deliveryFeeMode = "standard";
    d.deliveryFeeFlatZar = BigDecimal.valueOf(50.00);
    d.deliveryFeePerKmZar = BigDecimal.valueOf(8.00);
    d.storeLat = null;
    d.storeLng = null;
    d.eftBankInstructions = "";
    d.bankName = "";
    d.bankAccountHolder = "";
    d.bankAccountNumber = "";
    d.bankBranchCode = "";
    d.storeName = "";
    d.contactEmail = "";
    d.contactPhone = "";
    d.contactAddress = "";
    d.contactNotes = "";
    d.storeLogoUrl = "";
    d.storeHeroUrl = "";
    d.shopType = SalonAccessService.SHOP_NORMAL;
    d.openingHoursJson = null;
    d.acceptCustomerEft = Boolean.TRUE;
    d.acceptCustomerCash = Boolean.TRUE;
    d.createdAt = now;
    d.updatedAt = now;
    return d;
  }
}
