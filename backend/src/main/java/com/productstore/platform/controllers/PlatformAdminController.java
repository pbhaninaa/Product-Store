package com.productstore.platform.controllers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;

  public PlatformAdminController(
      UserRepository users, MembershipRepository memberships, PasswordHasher passwordHasher) {
    this.users = users;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
  }

  public record CreateSupportUserRequest(
      @Email @NotBlank String email, @NotBlank String password) {}

  @PostMapping("/support-users")
  @Transactional
  public Map<String, Object> createSupportUser(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody CreateSupportUserRequest req) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");

    // Must have PLATFORM_ADMIN membership (JWT filter sets roles; we double-check from DB for safety).
    boolean ok =
        memberships.findAllByUserId(principal.userId()).stream().anyMatch(m -> m.role == Role.PLATFORM_ADMIN);
    if (!ok) throw new IllegalArgumentException("forbidden");

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

    return Map.of("id", u.id.toString(), "email", u.email, "role", "SUPPORT_USER");
  }
}

