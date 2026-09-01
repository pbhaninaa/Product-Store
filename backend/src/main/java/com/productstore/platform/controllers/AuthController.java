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
import com.productstore.platform.services.PasswordResetService;
import com.productstore.platform.services.PlatformFeatureService;
import com.productstore.platform.services.SalonAccessService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private final PlatformFeatureService platformFeatures;
  private final PasswordResetService passwordResetService;

  public AuthController(
      UserRepository users,
      TenantRepository tenants,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      JwtService jwtService,
      SalonAccessService salonAccess,
      MerchantProvisioningService merchantProvisioning,
      PlatformFeatureService platformFeatures,
      PasswordResetService passwordResetService) {
    this.users = users;
    this.tenants = tenants;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.jwtService = jwtService;
    this.salonAccess = salonAccess;
    this.merchantProvisioning = merchantProvisioning;
    this.platformFeatures = platformFeatures;
    this.passwordResetService = passwordResetService;
  }

  public record RegisterMerchantRequest(
      @NotBlank String merchantName,
      String merchantSlug,
      @Email @NotBlank String ownerEmail,
      @NotBlank String ownerPassword,
      String invitedBy) {}

  @PostMapping("/register-merchant")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> registerMerchant(@Valid @RequestBody RegisterMerchantRequest req) {
    if (!platformFeatures.isEnabled(PlatformFeatureService.MERCHANT_SIGNUP)) {
      throw new IllegalStateException("feature_disabled");
    }
    // Slug is auto-generated from business name when omitted (public signup).
    var reg =
        merchantProvisioning.registerMerchant(
            req.merchantName(), req.merchantSlug(), req.ownerEmail(), req.ownerPassword(), req.invitedBy());
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

  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank String newPassword) {}

  public record ForgotPasswordRequest(@Email @NotBlank String email) {}

  public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}

  @PostMapping("/forgot-password")
  @Transactional
  public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
    passwordResetService.requestReset(req.email());
    return Map.of("ok", true);
  }

  @PostMapping("/reset-password")
  @Transactional
  public Map<String, Object> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    passwordResetService.resetPassword(req.token(), req.newPassword());
    return Map.of("ok", true);
  }

  @PostMapping("/change-password")
  @Transactional
  public Map<String, Object> changePassword(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest req) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    UserEntity user =
        users
            .findById(principal.userId())
            .orElseThrow(() -> new IllegalArgumentException("not_authenticated"));
    if (!passwordHasher.matches(req.currentPassword(), user.passwordHash)) {
      throw new IllegalArgumentException("invalid_credentials");
    }
    String next = req.newPassword() == null ? "" : req.newPassword();
    if (next.length() < 8) throw new IllegalArgumentException("password_too_short");
    user.passwordHash = passwordHasher.hash(next);
    users.save(user);
    return Map.of("ok", true);
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
    var user =
        users
            .findByEmailIgnoreCase(req.email().trim())
            .orElseThrow(() -> new IllegalArgumentException("invalid_credentials"));

    if (!passwordHasher.matches(req.password(), user.passwordHash)) {
      throw new IllegalArgumentException("invalid_credentials");
    }
    if (user.suspended) {
      throw new IllegalArgumentException("account_suspended");
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

  public record RegisterClientRequest(
      @Email @NotBlank String email, @NotBlank String password, String displayName, String invitedBy) {}

  @PostMapping("/register-client")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> registerClient(@Valid @RequestBody RegisterClientRequest req) {
    String email = req.email().trim().toLowerCase();
    if (users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalArgumentException("email_taken");
    String password = req.password() == null ? "" : req.password();
    if (password.length() < 8) throw new IllegalArgumentException("password_too_short");

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash(password);
    u.displayName = req.displayName() == null ? "" : req.displayName().trim();
    u.createdAt = Instant.now();
    users.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = null;
    m.role = Role.CLIENT;
    m.createdAt = Instant.now();
    memberships.save(m);

    try {
      merchantProvisioning.finishClientSignup(u, req.invitedBy());
    } catch (IllegalArgumentException ignored) {
      merchantProvisioning.finishClientSignup(u, null);
    }

    return issueLoginPayload(u);
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

    var client = ms.stream().filter(x -> x.role == Role.CLIENT).findFirst();
    if (client.isPresent()) {
      String token = jwtService.mintToken(user.id, user.email, List.of(Role.CLIENT), null, null);
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      out.put("token", token);
      out.put("roles", List.of("CLIENT"));
      out.put("tenant", null);
      out.put("email", user.email);
      out.put("displayName", user.displayName == null ? "" : user.displayName);
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

