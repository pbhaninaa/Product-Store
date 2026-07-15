package com.productstore.platform.controllers;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.auth.ApiUserPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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

  /** True until the first platform admin claims the empty system via signup. */
  @GetMapping("/setup-status")
  public Map<String, Object> setupStatus() {
    return Map.of("needsPlatformAdmin", needsPlatformAdmin());
  }

  public record RegisterPlatformAdminRequest(
      @Email @NotBlank String email, @NotBlank String password) {}

  /**
   * First signup only: creates a tenant-less {@link Role#PLATFORM_ADMIN}. After that, merchant signup
   * is open and this endpoint returns conflict.
   */
  @PostMapping("/register-platform-admin")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> registerPlatformAdmin(
      @Valid @RequestBody RegisterPlatformAdminRequest req) {
    return createPlatformAdminAccount(req.email(), req.password());
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
    // Older SPA builds only call register-merchant. On an empty system the first account is still the
    // system admin (owner email/password); store fields are ignored until an admin exists.
    if (needsPlatformAdmin()) {
      return createPlatformAdminAccount(req.ownerEmail(), req.ownerPassword());
    }
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

  private Map<String, Object> createPlatformAdminAccount(String rawEmail, String password) {
    if (!needsPlatformAdmin()) {
      throw new IllegalStateException(
          "A system admin already exists. Sign up as a Business Owner instead.");
    }

    String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
    if (email.isBlank()) throw new IllegalArgumentException("validation_error");
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw new IllegalArgumentException("email_taken");
    }

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash(password);
    u.createdAt = Instant.now();
    users.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = null;
    m.role = Role.PLATFORM_ADMIN;
    m.createdAt = Instant.now();
    memberships.save(m);

    String token = jwtService.mintToken(u.id, u.email, List.of(Role.PLATFORM_ADMIN), null, null);
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("roles", List.of(Role.PLATFORM_ADMIN.name()));
    out.put("tenant", null);
    out.put("claimedAsPlatformAdmin", true);
    return out;
  }

  private boolean needsPlatformAdmin() {
    return memberships.countByRole(Role.PLATFORM_ADMIN) == 0;
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
    return issueLoginPayload(user);
  }

  @PostMapping("/refresh")
  public Map<String, Object> refresh(@AuthenticationPrincipal ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    var user =
        users.findById(principal.userId()).orElseThrow(() -> new IllegalArgumentException("invalid_credentials"));
    return issueLoginPayload(user);
  }

  @PostMapping("/logout")
  public Map<String, Object> logout() {
    // Stateless JWT: client discards token. Endpoint exists for auth-flow parity.
    return Map.of("ok", true);
  }

  public record RegisterSupportRequest(@Email @NotBlank String email, @NotBlank String password) {}

  @PostMapping("/register-support")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> registerSupport(
      @AuthenticationPrincipal ApiUserPrincipal principal, @Valid @RequestBody RegisterSupportRequest req) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    boolean isPlatformAdmin = principal.roles().stream().anyMatch(r -> r == Role.PLATFORM_ADMIN);
    if (!isPlatformAdmin) throw new IllegalArgumentException("forbidden");

    String email = req.email().trim().toLowerCase();
    if (users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalArgumentException("email_taken");

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash(req.password());
    u.createdAt = Instant.now();
    users.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = null;
    m.role = Role.SUPPORT_USER;
    m.createdAt = Instant.now();
    memberships.save(m);

    return Map.of("id", u.id.toString(), "email", u.email, "role", Role.SUPPORT_USER.name());
  }

  private Map<String, Object> issueLoginPayload(UserEntity user) {
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
    m.put(
        "subscriptionPlan",
        tenant.subscriptionPlan == null
            ? com.productstore.platform.entities.TenantEntity.SubscriptionPlan.STARTER.name()
            : tenant.subscriptionPlan.name());
    m.put("shopType", salonAccess.normalizedShopType(tenant.id));
    return m;
  }

}

