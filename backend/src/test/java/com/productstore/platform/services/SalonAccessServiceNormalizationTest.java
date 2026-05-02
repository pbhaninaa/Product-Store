package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SalonAccessServiceNormalizationTest {

  @Test
  void normalizedShopTypeStringDefaults() {
    assertThat(SalonAccessService.normalizedShopType((String) null))
        .isEqualTo(SalonAccessService.SHOP_NORMAL);
    assertThat(SalonAccessService.normalizedShopType("")).isEqualTo(SalonAccessService.SHOP_NORMAL);
    assertThat(SalonAccessService.normalizedShopType("  ")).isEqualTo(SalonAccessService.SHOP_NORMAL);
  }

  @Test
  void legacySalonMapsToHybrid() {
    assertThat(SalonAccessService.normalizedShopType("salon")).isEqualTo(SalonAccessService.SHOP_SALON_AND_STORE);
    assertThat(SalonAccessService.normalizedShopType("SALON")).isEqualTo(SalonAccessService.SHOP_SALON_AND_STORE);
  }

  @Test
  void hybridAndSalonOnly() {
    assertThat(SalonAccessService.normalizedShopType("salon_and_store")).isEqualTo(SalonAccessService.SHOP_SALON_AND_STORE);
    assertThat(SalonAccessService.normalizedShopType("salon_only")).isEqualTo(SalonAccessService.SHOP_SALON_ONLY);
    assertThat(SalonAccessService.normalizedShopType(" SALON_ONLY ")).isEqualTo(SalonAccessService.SHOP_SALON_ONLY);
  }

  @Test
  void unknownBecomesNormal() {
    assertThat(SalonAccessService.normalizedShopType("bakery")).isEqualTo(SalonAccessService.SHOP_NORMAL);
  }

  @Test
  void canonicalForSaveMatchesNormalized() {
    assertThat(SalonAccessService.canonicalShopTypeForSave("salon")).isEqualTo(SalonAccessService.SHOP_SALON_AND_STORE);
    assertThat(SalonAccessService.canonicalShopTypeForSave("normal_store")).isEqualTo(SalonAccessService.SHOP_NORMAL);
  }
}
