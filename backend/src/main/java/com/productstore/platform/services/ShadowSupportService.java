package com.productstore.platform.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShadowSupportService {
  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final JwtService jwtService;
  private final PlatformFeatureService features;
  private final SupportAuditService audit;
  private final SupportAccessService access;

  public ShadowSupportService(
      TenantRepository tenants,
      MembershipRepository memberships,
      UserRepository users,
      JwtService jwtService,
      PlatformFeatureService features,
      SupportAuditService audit,
      SupportAccessService access) {
    this.tenants = tenants;
    this.memberships = memberships;
    this.users = users;
    this.jwtService = jwtService;
    this.features = features;
    this.audit = audit;
    this.access = access;
  }

  public List<Map<String, Object>> listCandidates(String q) {
    String needle = q == null ? "" : q.trim().toLowerCase();
    return tenants.searchMerchants(needle).stream()
        .map(
            t -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", t.id.toString());
              m.put("slug", t.slug);
              m.put("name", t.name);
              m.put(
                  "ownerEmail",
                  memberships.findAllByTenantIdAndRole(t.id, Role.MERCHANT_OWNER).stream()
                      .findFirst()
                      .flatMap(mem -> users.findById(mem.userId))
                      .map(u -> u.email)
                      .orElse(""));
              return m;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> mintShadowToken(ApiUserPrincipal actor, String slugRaw) {
    access.requirePermission(actor, SupportPermission.USE_SHADOW);
    if (!features.isEnabled(PlatformFeatureService.SHADOW_SUPPORT)) {
      throw new IllegalStateException("feature_disabled");
    }
    String slug = slugRaw == null ? "" : slugRaw.trim();
    if (slug.isEmpty()) throw new IllegalArgumentException("slug_required");
    TenantEntity tenant =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));
    MembershipEntity ownerMem =
        memberships.findAllByTenantIdAndRole(tenant.id, Role.MERCHANT_OWNER).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("merchant_owner_missing"));
    UserEntity owner =
        users.findById(ownerMem.userId).orElseThrow(() -> new IllegalStateException("owner_missing"));
    if (owner.suspended) throw new IllegalStateException("owner_suspended");

    String token =
        jwtService.mintToken(
            owner.id, owner.email, List.of(Role.MERCHANT_OWNER), tenant.id, tenant.slug, true);
    audit.record(
        actor,
        "SHADOW_ENTER",
        "TENANT",
        tenant.id.toString(),
        "shadow into " + tenant.slug + " as " + owner.email);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("tenant", Map.of("id", tenant.id.toString(), "slug", tenant.slug, "name", tenant.name));
    out.put("ownerEmail", owner.email);
    out.put("shadowSupport", true);
    return out;
  }
}
