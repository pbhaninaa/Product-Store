package com.productstore.platform.controllers;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantProvisioningService;
import com.productstore.platform.services.SalonAccessService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserRepository users;
  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;
  private final JwtService jwtService;
  private final SalonAccessService salonAccess;
  private final MerchantProvisioningService merchantProvisioning;

  public AuthController(
      UserRepository users,
      TenantRepository tenants,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      JwtService jwtService,
      SalonAccessService salonAccess,
      MerchantProvisioningService merchantProvisioning) {
    this.users = users;
    this.tenants = tenants;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.jwtService = jwtService;
    this.salonAccess = salonAccess;
    this.merchantProvisioning = merchantProvisioning;
  }

  public record RegisterMerchantRequest(
      @NotBlank String merchantName,
      @NotBlank String merchantSlug,
      @Email @NotBlank String ownerEmail,
      @NotBlank String ownerPassword) {}

  @PostMapping("/register-merchant")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> registerMerchant(@Valid @RequestBody RegisterMerchantRequest req) {
    var reg =
        merchantProvisioning.registerMerchant(
            req.merchantName(), req.merchantSlug(), req.ownerEmail(), req.ownerPassword());
    TenantEntity t = reg.tenant();
    UserEntity u = reg.owner();

    String token = jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), t.id, t.slug);
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("merchantSlug", t.slug);
    out.put("tenant", tenantSnapshot(t));
    out.put("roles", List.of(Role.MERCHANT_OWNER.name()));
    return out;
  }

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
    var user =
        users
            .findByEmailIgnoreCase(req.email().trim())
            .orElseThrow(() -> new IllegalArgumentException("invalid_credentials"));

    if (!passwordHasher.matches(req.password(), user.passwordHash)) {
      throw new IllegalArgumentException("invalid_credentials");
    }

    var ms = memberships.findAllByUserId(user.id);
    var platformAdmin = ms.stream().filter(x -> x.role == Role.PLATFORM_ADMIN).findFirst();
    if (platformAdmin.isPresent()) {
      String token = jwtService.mintToken(user.id, user.email, List.of(Role.PLATFORM_ADMIN), null, null);
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      out.put("token", token);
      out.put("roles", List.of("PLATFORM_ADMIN"));
      out.put("tenant", null);
      return out;
    }

    var support = ms.stream().filter(x -> x.role == Role.SUPPORT_USER).findFirst();
    if (support.isPresent()) {
      String token = jwtService.mintToken(user.id, user.email, List.of(Role.SUPPORT_USER), null, null);
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      out.put("token", token);
      out.put("roles", List.of("SUPPORT_USER"));
      out.put("tenant", null);
      return out;
    }

    var merchantMembership =
        ms.stream()
            .filter(x -> x.role == Role.MERCHANT_OWNER || x.role == Role.MERCHANT_STAFF)
            .min(
                Comparator.comparing((MembershipEntity x) -> x.role == Role.MERCHANT_OWNER ? 0 : 1)
                    .thenComparing(
                        (MembershipEntity x) ->
                            x.createdAt == null ? Instant.EPOCH : x.createdAt))
            .orElseThrow(() -> new IllegalArgumentException("no_membership"));

    var tenant =
        tenants
            .findById(merchantMembership.tenantId)
            .orElseThrow(() -> new IllegalArgumentException("tenant_missing"));

    String token =
        jwtService.mintToken(
            user.id,
            user.email,
            List.of(merchantMembership.role),
            tenant.id,
            tenant.slug);
    return Map.of(
        "token",
        token,
        "merchantSlug",
        tenant.slug,
        "tenant",
        tenantSnapshot(tenant),
        "roles",
        List.of(merchantMembership.role.name()));
  }

  private Map<String, Object> tenantSnapshot(TenantEntity tenant) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
    m.put("id", tenant.id.toString());
    m.put("slug", tenant.slug);
    m.put("name", tenant.name == null ? "" : tenant.name);
    m.put("shopType", salonAccess.normalizedShopType(tenant.id));
    return m;
  }

}

