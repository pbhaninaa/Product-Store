package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.PeachPaymentMethod;
import com.productstore.platform.models.PayFastCheckoutResponse;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PayFastPaymentService;
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
  private final PayFastPaymentService payFastPaymentService;

  public AdminSubscriptionController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      MerchantSubscriptionService subscriptions,
      PayFastPaymentService payFastPaymentService) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.subscriptions = subscriptions;
    this.payFastPaymentService = payFastPaymentService;
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
    return payfastCheckout(merchantSlug, principal, body);
  }

  @PostMapping("/payfast-checkout")
  public Map<String, Object> payfastCheckout(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    Object methodRaw =
        body == null
            ? null
            : (body.get("payFastPaymentMethod") != null
                ? body.get("payFastPaymentMethod")
                : body.get("peachPaymentMethod"));
    PeachPaymentMethod peachPaymentMethod =
        PeachPaymentMethod.fromRequest(methodRaw == null ? null : String.valueOf(methodRaw));
    PayFastCheckoutResponse session =
        payFastPaymentService.initiateSubscriptionCheckout(
            tenant.id(), merchantSlug, peachPaymentMethod);
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("paymentId", session.paymentId());
    out.put("processUrl", session.processUrl());
    out.put("fields", session.fields());
    return out;
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
