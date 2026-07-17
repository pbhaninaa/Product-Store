package com.productstore.platform.services;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;

@Service
public class SupportAccessService {
  private final MembershipRepository memberships;

  public SupportAccessService(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  public void requireSupport(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    List<Role> roles = principal.roles() != null ? principal.roles() : List.of();
    boolean ok = roles.contains(Role.SUPPORT_USER) || roles.contains(Role.PLATFORM_ADMIN);
    if (!ok) throw new IllegalArgumentException("forbidden");
  }

  public void requirePlatformAdmin(ApiUserPrincipal principal) {
    requireSupport(principal);
    if (!principal.roles().contains(Role.PLATFORM_ADMIN)) {
      throw new IllegalArgumentException("forbidden");
    }
  }

  public void requirePermission(ApiUserPrincipal principal, SupportPermission permission) {
    requireSupport(principal);
    if (principal.roles().contains(Role.PLATFORM_ADMIN)) return;
    Set<SupportPermission> perms = resolvePermissions(principal.userId());
    if (!perms.contains(permission)) {
      throw new IllegalArgumentException("forbidden");
    }
  }

  public Set<SupportPermission> resolvePermissions(UUID userId) {
    Set<SupportPermission> out = new LinkedHashSet<>();
    for (MembershipEntity m : memberships.findAllByUserId(userId)) {
      if (m.role == Role.PLATFORM_ADMIN) {
        return new LinkedHashSet<>(SupportPermission.ALL);
      }
      if (m.role == Role.SUPPORT_USER) {
        Set<SupportPermission> parsed = SupportPermission.parseCsv(m.permissions);
        if (parsed.isEmpty()) {
          out.addAll(SupportPermission.DEFAULT_SUPPORT);
        } else {
          out.addAll(parsed);
        }
      }
    }
    return out;
  }

  public Map<String, Object> permissionsPayload(ApiUserPrincipal principal) {
    requireSupport(principal);
    boolean admin = principal.roles().contains(Role.PLATFORM_ADMIN);
    Set<SupportPermission> perms =
        admin ? new LinkedHashSet<>(SupportPermission.ALL) : resolvePermissions(principal.userId());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("platformAdmin", admin);
    m.put("permissions", SupportPermission.names(perms));
    return m;
  }
}
