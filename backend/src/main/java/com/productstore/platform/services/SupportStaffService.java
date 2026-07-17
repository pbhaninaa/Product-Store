package com.productstore.platform.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportStaffService {
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;
  private final SupportAuditService audit;
  private final SupportAccessService access;

  public SupportStaffService(
      UserRepository users,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      SupportAuditService audit,
      SupportAccessService access) {
    this.users = users;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.audit = audit;
    this.access = access;
  }

  public List<Map<String, Object>> listStaff() {
    List<MembershipEntity> rows =
        memberships.findAllByRoleIn(List.of(Role.SUPPORT_USER, Role.PLATFORM_ADMIN));
    List<Map<String, Object>> out = new ArrayList<>();
    Set<UUID> seen = new LinkedHashSet<>();
    for (MembershipEntity m : rows) {
      if (!seen.add(m.userId)) continue;
      users.findById(m.userId).ifPresent(u -> out.add(toMap(u, m)));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> createSupportUser(
      ApiUserPrincipal actor, String emailRaw, String password, List<String> permissionNames) {
    access.requirePermission(actor, SupportPermission.MANAGE_STAFF);
    String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
    if (email.isEmpty()) throw new IllegalArgumentException("email_required");
    if (password == null || password.length() < 8) throw new IllegalArgumentException("password_too_short");
    if (users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalArgumentException("email_taken");

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash(password);
    u.createdAt = Instant.now();
    u.suspended = false;
    users.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = null;
    m.role = Role.SUPPORT_USER;
    m.createdAt = Instant.now();
    Set<SupportPermission> perms = SupportPermission.parseCsv(String.join(",", permissionNames == null ? List.of() : permissionNames));
    if (perms.isEmpty()) perms = new LinkedHashSet<>(SupportPermission.DEFAULT_SUPPORT);
    m.permissions = SupportPermission.toCsv(perms);
    memberships.save(m);
    audit.record(actor, "STAFF_CREATE", "USER", u.id.toString(), email);
    return toMap(u, m);
  }

  @Transactional
  public Map<String, Object> setSuspended(ApiUserPrincipal actor, UUID userId, boolean suspended) {
    access.requirePermission(actor, SupportPermission.MANAGE_STAFF);
    access.requirePlatformAdmin(actor);
    UserEntity u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("user_not_found"));
    if (actor.userId().equals(userId)) throw new IllegalArgumentException("cannot_suspend_self");
    u.suspended = suspended;
    users.save(u);
    audit.record(
        actor,
        suspended ? "STAFF_SUSPEND" : "STAFF_ACTIVATE",
        "USER",
        userId.toString(),
        u.email);
    MembershipEntity m =
        memberships.findAllByUserId(userId).stream()
            .filter(x -> x.role == Role.SUPPORT_USER || x.role == Role.PLATFORM_ADMIN)
            .findFirst()
            .orElse(null);
    return toMap(u, m);
  }

  @Transactional
  public Map<String, Object> resetPassword(ApiUserPrincipal actor, UUID userId, String newPassword) {
    access.requirePermission(actor, SupportPermission.MANAGE_STAFF);
    access.requirePlatformAdmin(actor);
    if (newPassword == null || newPassword.length() < 8) {
      throw new IllegalArgumentException("password_too_short");
    }
    UserEntity u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("user_not_found"));
    u.passwordHash = passwordHasher.hash(newPassword);
    users.save(u);
    audit.record(actor, "STAFF_RESET_PASSWORD", "USER", userId.toString(), u.email);
    return Map.of("ok", true, "userId", userId.toString());
  }

  @Transactional
  public Map<String, Object> updatePermissions(
      ApiUserPrincipal actor, UUID userId, List<String> permissionNames) {
    access.requirePermission(actor, SupportPermission.MANAGE_STAFF);
    access.requirePlatformAdmin(actor);
    MembershipEntity m =
        memberships.findAllByUserId(userId).stream()
            .filter(x -> x.role == Role.SUPPORT_USER)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("support_user_not_found"));
    Set<SupportPermission> perms =
        SupportPermission.parseCsv(String.join(",", permissionNames == null ? List.of() : permissionNames));
    m.permissions = SupportPermission.toCsv(perms);
    memberships.save(m);
    UserEntity u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("user_not_found"));
    audit.record(actor, "STAFF_PERMISSIONS", "USER", userId.toString(), m.permissions);
    return toMap(u, m);
  }

  private Map<String, Object> toMap(UserEntity u, MembershipEntity m) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", u.id.toString());
    out.put("email", u.email);
    out.put("suspended", u.suspended);
    out.put("createdAt", u.createdAt != null ? u.createdAt.toString() : null);
    if (m != null) {
      out.put("role", m.role.name());
      Set<SupportPermission> perms =
          m.role == Role.PLATFORM_ADMIN
              ? new LinkedHashSet<>(SupportPermission.ALL)
              : SupportPermission.parseCsv(m.permissions);
      if (m.role == Role.SUPPORT_USER && perms.isEmpty()) {
        perms = new LinkedHashSet<>(SupportPermission.DEFAULT_SUPPORT);
      }
      out.put("permissions", SupportPermission.names(perms));
    }
    return out;
  }
}
