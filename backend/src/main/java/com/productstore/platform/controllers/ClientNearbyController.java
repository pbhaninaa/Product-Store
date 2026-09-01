package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.services.NearbyMerchantSearchService;
import com.productstore.platform.services.NearbyMerchantSearchService.Kind;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/nearby")
public class ClientNearbyController {
  private final NearbyMerchantSearchService nearby;

  public ClientNearbyController(NearbyMerchantSearchService nearby) {
    this.nearby = nearby;
  }

  @GetMapping("/offerings")
  public List<Map<String, Object>> offerings(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam String kind,
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam(required = false) Double radiusKm,
      @RequestParam(required = false) String q) {
    requireClient(principal);
    Kind k = NearbyMerchantSearchService.parseKind(kind);
    return nearby.listOfferings(k, latitude, longitude, NearbyMerchantSearchService.normalizeRadiusKm(radiusKm), q);
  }

  @GetMapping("/merchants")
  public List<Map<String, Object>> merchants(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam String kind,
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam(required = false) Double radiusKm,
      @RequestParam(required = false) String names,
      @RequestParam(required = false) String services) {
    requireClient(principal);
    Kind k = NearbyMerchantSearchService.parseKind(kind);
    String csv = names != null && !names.isBlank() ? names : services;
    return nearby.listMerchants(
        k, latitude, longitude, NearbyMerchantSearchService.normalizeRadiusKm(radiusKm), csv);
  }

  private static void requireClient(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    boolean ok = principal.roles().stream().anyMatch(r -> r == Role.CLIENT);
    if (!ok) throw new IllegalArgumentException("forbidden");
  }
}
