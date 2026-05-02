package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import com.productstore.platform.services.multitenancy.TenantContext;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping("/api/health")
  public Map<String, Object> health() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("tenantSlug", TenantContext.getTenantSlug().orElse(null));
    return out;
  }
}

