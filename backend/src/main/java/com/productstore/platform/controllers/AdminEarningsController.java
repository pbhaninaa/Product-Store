package com.productstore.platform.controllers;

import java.time.LocalDate;

import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.StoreEarningsService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The store owner's in-app earnings and payouts (plan §4 endpoint 8). Owner only, own store only. */
@RestController
@RequestMapping("/api/m/{merchantSlug}/admin")
public class AdminEarningsController {
  private final TenantAccessService tenantAccess;
  private final MerchantAccessService access;
  private final StoreEarningsService storeEarnings;

  public AdminEarningsController(
      TenantAccessService tenantAccess, MerchantAccessService access, StoreEarningsService storeEarnings) {
    this.tenantAccess = tenantAccess;
    this.access = access;
    this.storeEarnings = storeEarnings;
  }

  @GetMapping("/store-earnings")
  public PayoutDtos.StoreEarnings storeEarnings(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    return storeEarnings.forStore(tenant.id(), startDate, endDate);
  }
}
