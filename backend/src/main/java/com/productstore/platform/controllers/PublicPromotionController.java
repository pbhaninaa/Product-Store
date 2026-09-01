package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.services.PromotionService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/promotions")
public class PublicPromotionController {
  private final TenantAccessService tenantAccess;
  private final PromotionService promotions;

  public PublicPromotionController(TenantAccessService tenantAccess, PromotionService promotions) {
    this.tenantAccess = tenantAccess;
    this.promotions = promotions;
  }

  @GetMapping
  public List<Map<String, Object>> listActive(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return promotions.toMaps(promotions.listActiveToday(tenant.id()));
  }
}
