package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PaystackPaymentService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/subscription")
public class AdminSubscriptionController {
  private final TenantAccessService tenantAccess;
  private final MerchantAccessService access;
  private final MerchantSubscriptionService subscriptions;
  private final PaystackPaymentService paystack;

  public AdminSubscriptionController(
      TenantAccessService tenantAccess,
      MerchantAccessService access,
      MerchantSubscriptionService subscriptions,
      PaystackPaymentService paystack) {
    this.tenantAccess = tenantAccess;
    this.access = access;
    this.subscriptions = subscriptions;
    this.paystack = paystack;
  }

  @GetMapping("/me")
  public Map<String, Object> me(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return subscriptions.buildStatus(tenant.id());
  }

  @GetMapping("/plans")
  public Map<String, Object> plans(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return Map.of("plans", subscriptions.listPlans());
  }

  @PutMapping("/plan")
  public Map<String, Object> choosePlan(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
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
    access.requireOwner(principal, tenant.id());
    throw new IllegalArgumentException("manual_eft_disabled");
  }

  /**
   * Return page status of one online subscription payment of this merchant. Confirms a still pending
   * payment with Paystack. Response: {@code { paymentId, status, paid, cancelled }}.
   */
  @GetMapping("/online-payments/{paymentId}/status")
  public Map<String, Object> onlinePaymentStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID paymentId,
      @RequestParam(value = "reference", required = false) String reference,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return paystack.subscriptionPaymentStatus(tenant.id(), paymentId, reference);
  }

  /** Owner starts an online subscription payment. Body: {@code { onlinePaymentChannel: "CARD" }} (card only). */
  @PostMapping("/online-checkout")
  public Map<String, Object> onlineCheckout(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    Object raw = body == null ? null : body.get("onlinePaymentChannel");
    OnlinePaymentChannel channel = OnlinePaymentChannel.fromRequest(raw == null ? null : String.valueOf(raw));
    return paystack.initiateSubscriptionCheckout(tenant.id(), merchantSlug, channel).toResponse();
  }
}
