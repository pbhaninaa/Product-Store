package com.productstore.platform.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.PromotionEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.PromotionService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/promotions")
public class AdminPromotionController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final PromotionService promotions;

  public AdminPromotionController(
      TenantAccessService tenantAccess, MembershipRepository memberships, PromotionService promotions) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.promotions = promotions;
  }

  public record PromotionBody(
      String title,
      String description,
      String discountType,
      Double discountValue,
      Double minimumOrderValue,
      String startDate,
      String endDate,
      Boolean active,
      String applicableCategories,
      Integer usageLimit) {}

  @GetMapping
  public List<Map<String, Object>> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return promotions.toMaps(promotions.listMine(tenant.id()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody PromotionBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return promotions.toMap(promotions.create(tenant.id(), fromBody(body)));
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable String merchantSlug,
      @PathVariable UUID id,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody PromotionBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return promotions.toMap(promotions.update(tenant.id(), id, fromBody(body)));
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(
      @PathVariable String merchantSlug,
      @PathVariable UUID id,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    promotions.delete(tenant.id(), id);
    return Map.of("ok", true);
  }

  private static PromotionEntity fromBody(PromotionBody body) {
    if (body == null) throw new IllegalArgumentException("Promotion body is required");
    PromotionEntity p = new PromotionEntity();
    p.title = body.title();
    p.description = body.description();
    p.discountType = body.discountType();
    p.discountValue = body.discountValue();
    p.minimumOrderValue = body.minimumOrderValue();
    p.startDate = body.startDate() == null || body.startDate().isBlank() ? null : LocalDate.parse(body.startDate());
    p.endDate = body.endDate() == null || body.endDate().isBlank() ? null : LocalDate.parse(body.endDate());
    p.active = body.active() == null || Boolean.TRUE.equals(body.active());
    p.applicableCategories = body.applicableCategories();
    p.usageLimit = body.usageLimit();
    return p;
  }

  private void requireMerchant(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}
