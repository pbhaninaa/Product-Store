package com.productstore.platform.controllers;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.SupportAccessService;
import com.productstore.platform.services.SupportAuditService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/subscriptions")
public class SupportSubscriptionController {
  private final MerchantSubscriptionService subscriptions;
  private final SupportAccessService access;
  private final SupportAuditService audit;

  public SupportSubscriptionController(
      MerchantSubscriptionService subscriptions,
      SupportAccessService access,
      SupportAuditService audit) {
    this.subscriptions = subscriptions;
    this.access = access;
    this.audit = audit;
  }

  @GetMapping
  public Map<String, Object> listAll(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    return Map.of("subscriptions", subscriptions.listMerchantSubscriptions());
  }

  @GetMapping("/plans")
  public Map<String, Object> plans(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    return Map.of("plans", subscriptions.listPlans());
  }

  @PutMapping("/plans/{tier}")
  public Map<String, Object> updatePlan(
      @PathVariable String tier,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    access.requirePermission(principal, SupportPermission.MANAGE_PLANS);
    TenantEntity.SubscriptionPlan plan =
        TenantEntity.SubscriptionPlan.valueOf(tier.trim().toUpperCase());
    Map<String, Object> updated = subscriptions.updatePlan(plan, body);
    audit.record(principal, "PLAN_UPDATE", "PLAN", plan.name(), String.valueOf(body));
    return updated;
  }

  @GetMapping("/pending-proofs")
  public Map<String, Object> pending(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    return Map.of("pending", subscriptions.listPendingProofs());
  }

  @PostMapping("/{tenantId}/approve-proof")
  public Map<String, Object> approve(
      @PathVariable UUID tenantId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    throw new IllegalArgumentException("subscription_proof_mutation_disabled");
  }

  @PostMapping("/{tenantId}/reject-proof")
  public Map<String, Object> reject(
      @PathVariable UUID tenantId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    throw new IllegalArgumentException("subscription_proof_mutation_disabled");
  }

  @PostMapping("/{tenantId}/activate")
  public Map<String, Object> activate(
      @PathVariable UUID tenantId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    access.requirePlatformAdmin(principal);
    throw new IllegalArgumentException("manual_subscription_activation_disabled");
  }

  @GetMapping("/{tenantId}/proof-file")
  public ResponseEntity<FileSystemResource> proofFile(
      @PathVariable UUID tenantId, @AuthenticationPrincipal ApiUserPrincipal principal)
      throws Exception {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    var path = subscriptions.resolveProofFile(tenantId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"proof.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(Files.size(path))
        .body(new FileSystemResource(path));
  }

  @GetMapping("/platform-banking")
  public Map<String, Object> getBanking(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    return subscriptions.getPlatformBanking();
  }

  @PutMapping("/platform-banking")
  public Map<String, Object> putBanking(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody Map<String, Object> body) {
    access.requirePermission(principal, SupportPermission.MANAGE_SUBSCRIPTIONS);
    throw new IllegalArgumentException("platform_banking_mutation_disabled");
  }
}
