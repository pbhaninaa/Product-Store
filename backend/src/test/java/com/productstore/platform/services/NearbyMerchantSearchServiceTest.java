package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NearbyMerchantSearchServiceTest {
  @Test
  void haversine_johannesburgToPretoria_isAbout55km() {
    double km = NearbyMerchantSearchService.haversineKm(-26.2041, 28.0473, -25.7479, 28.2293);
    assertTrue(km > 45 && km < 70, "expected ~55km, got " + km);
  }

  @Test
  void normalizeRadius_defaultsAndCaps() {
    org.junit.jupiter.api.Assertions.assertEquals(50d, NearbyMerchantSearchService.normalizeRadiusKm(null));
    org.junit.jupiter.api.Assertions.assertEquals(200d, NearbyMerchantSearchService.normalizeRadiusKm(999d));
    org.junit.jupiter.api.Assertions.assertEquals(25d, NearbyMerchantSearchService.normalizeRadiusKm(25d));
  }
}
