package com.productstore.platform.controllers;

import java.util.Map;

import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in member's own effective permissions in this store, so the admin UI can hide what they can't use. */
@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/access")
public class AdminAccessController {
  private final TenantAccessService tenantAccess;
  private final MerchantAccessService access;

  public AdminAccessController(TenantAccessService tenantAccess, MerchantAccessService access) {
    this.tenantAccess = tenantAccess;
    this.access = access;
  }

  @GetMapping("/me")
  public Map<String, Object> me(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return access.describe(access.requireMember(principal, tenant.id()));
  }
}
