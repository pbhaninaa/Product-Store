package com.productstore.platform.controllers;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

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

  public SupportSubscriptionController(MerchantSubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @GetMapping("/pending-proofs")
  public Map<String, Object> pending(@AuthenticationPrincipal ApiUserPrincipal principal) {
    requireSupport(principal);
    return Map.of("pending", subscriptions.listPendingProofs());
  }

  @PostMapping("/{tenantId}/approve-proof")
  public Map<String, Object> approve(
      @PathVariable UUID tenantId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    requireSupport(principal);
    return subscriptions.approveProof(tenantId);
  }

  @PostMapping("/{tenantId}/reject-proof")
  public Map<String, Object> reject(
      @PathVariable UUID tenantId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    requireSupport(principal);
    String note = body != null && body.get("note") != null ? String.valueOf(body.get("note")) : "";
    return subscriptions.rejectProof(tenantId, note);
  }

  @PostMapping("/{tenantId}/activate")
  public Map<String, Object> activate(
      @PathVariable UUID tenantId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    requirePlatformAdmin(principal);
    if (body != null && body.get("tier") != null) {
      var tier =
          com.productstore.platform.entities.TenantEntity.SubscriptionPlan.valueOf(
              String.valueOf(body.get("tier")).trim().toUpperCase());
      return subscriptions.forceActivatePlan(tenantId, tier);
    }
    return subscriptions.activatePeriod(tenantId);
  }

  @GetMapping("/{tenantId}/proof-file")
  public ResponseEntity<FileSystemResource> proofFile(
      @PathVariable UUID tenantId, @AuthenticationPrincipal ApiUserPrincipal principal)
      throws Exception {
    requireSupport(principal);
    var path = subscriptions.resolveProofFile(tenantId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"proof.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(Files.size(path))
        .body(new FileSystemResource(path));
  }

  @GetMapping("/platform-banking")
  public Map<String, Object> getBanking(@AuthenticationPrincipal ApiUserPrincipal principal) {
    requireSupport(principal);
    return subscriptions.getPlatformBanking();
  }

  @PutMapping("/platform-banking")
  public Map<String, Object> putBanking(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody Map<String, Object> body) {
    requireSupport(principal);
    return subscriptions.updatePlatformBanking(body);
  }

  private void requireSupport(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    List<Role> roles = principal.roles() != null ? principal.roles() : List.of();
    boolean ok =
        roles.contains(Role.SUPPORT_USER) || roles.contains(Role.PLATFORM_ADMIN);
    if (!ok) throw new IllegalArgumentException("forbidden");
  }

  private void requirePlatformAdmin(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    List<Role> roles = principal.roles() != null ? principal.roles() : List.of();
    if (!roles.contains(Role.PLATFORM_ADMIN)) throw new IllegalArgumentException("forbidden");
  }
}
