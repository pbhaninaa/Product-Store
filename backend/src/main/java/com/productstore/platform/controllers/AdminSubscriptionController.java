package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.PeachPaymentMethod;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PeachPaymentService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/subscription")
public class AdminSubscriptionController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final MerchantSubscriptionService subscriptions;
  private final PeachPaymentService peachPaymentService;

  public AdminSubscriptionController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      MerchantSubscriptionService subscriptions,
      PeachPaymentService peachPaymentService) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.subscriptions = subscriptions;
    this.peachPaymentService = peachPaymentService;
  }

  @GetMapping("/me")
  public Map<String, Object> me(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return subscriptions.buildStatus(tenant.id());
  }

  @GetMapping("/plans")
  public Map<String, Object> plans(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return Map.of("plans", subscriptions.listPlans());
  }

  @PutMapping("/plan")
  public Map<String, Object> choosePlan(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    TenantEntity.SubscriptionPlan tier =
        TenantEntity.SubscriptionPlan.valueOf(String.valueOf(body.get("tier")).trim().toUpperCase());
    return subscriptions.choosePlan(tenant.id(), tier);
  }

  @PostMapping(value = "/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> uploadProof(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestPart("file") MultipartFile file) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    throw new IllegalArgumentException("manual_eft_disabled");
  }

  @PostMapping("/peach-checkout")
  public Map<String, Object> peachCheckout(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    Object peachMethodRaw = body == null ? null : body.get("peachPaymentMethod");
    PeachPaymentMethod peachPaymentMethod =
        PeachPaymentMethod.fromRequest(peachMethodRaw == null ? null : String.valueOf(peachMethodRaw));
    PeachPaymentService.PeachCheckoutSession session =
        peachPaymentService.initiateSubscriptionCheckout(
            tenant.id(), merchantSlug, peachPaymentMethod);
    return Map.of("checkoutId", session.checkoutId(), "redirectUrl", session.redirectUrl());
  }

  @GetMapping("/platform-banking")
  public Map<String, Object> platformBanking(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    return subscriptions.getPlatformBanking();
  }

  private void requireOwner(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }

  private void requireMerchant(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}
