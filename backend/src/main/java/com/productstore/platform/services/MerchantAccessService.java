package com.productstore.platform.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.productstore.platform.constants.MerchantPermission;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides who may do what in a merchant's admin. Every {@code /api/m/{slug}/admin}
 * controller calls this instead of checking roles itself.
 *
 * <p>Permissions are read from the database on every request (never from the JWT), so an owner's
 * change takes effect on the staff member's very next request.
 *
 * <p>Errors: {@code not_authenticated} / {@code forbidden} (403) when the caller is not a member of
 * this store; {@code permission_denied} (403) when they are a member but lack the permission or the
 * action is owner-only.
 */
@Service
public class MerchantAccessService {
  public static final String PERMISSION_DENIED = "permission_denied";

  /** Actions no permission can unlock; shown to the owner next to the permissions editor. */
  public static final List<String> OWNER_ONLY_ACTIONS =
      List.of(
          "Subscription & billing",
          "Store banking details",
          "Adding, editing and removing staff",
          "Changing anyone's permissions");

  private final MembershipRepository memberships;
  private final EmployeeRepository employees;

  public MerchantAccessService(MembershipRepository memberships, EmployeeRepository employees) {
    this.memberships = memberships;
    this.employees = employees;
  }

  /** What the caller may do in one store. */
  public record MerchantAccess(UUID userId, UUID tenantId, boolean owner, Set<MerchantPermission> permissions) {
    public boolean has(MerchantPermission p) {
      return owner || permissions.contains(p);
    }
  }

  /** Any owner or staff member of this store (notifications, help, own income, reading settings). */
  public MerchantAccess requireMember(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    List<MembershipEntity> mine =
        memberships.findAllByUserId(principal.userId()).stream()
            .filter(m -> tenantId != null && tenantId.equals(m.tenantId))
            .toList();
    if (mine.stream().anyMatch(m -> m.role == Role.MERCHANT_OWNER)) {
      return new MerchantAccess(principal.userId(), tenantId, true, EnumSet.allOf(MerchantPermission.class));
    }
    if (mine.stream().noneMatch(m -> m.role == Role.MERCHANT_STAFF)) {
      throw new IllegalArgumentException("forbidden");
    }
    Set<MerchantPermission> perms =
        employees
            .findFirstByTenantIdAndUserProfileId(tenantId, principal.userId())
            .map(e -> MerchantPermission.resolveStored(e.merchantPermissions))
            // A staff membership without an employee row predates payroll: keep today's access.
            .orElseGet(() -> EnumSet.copyOf(MerchantPermission.LEGACY_STAFF_DEFAULT));
    return new MerchantAccess(principal.userId(), tenantId, false, perms);
  }

  /** Owner-only actions (billing, banking, hiring, permissions). */
  public MerchantAccess requireOwner(ApiUserPrincipal principal, UUID tenantId) {
    MerchantAccess a = requireMember(principal, tenantId);
    if (!a.owner()) throw new IllegalArgumentException(PERMISSION_DENIED);
    return a;
  }

  /** Caller must hold every listed permission (the owner always does). */
  public MerchantAccess requireAll(ApiUserPrincipal principal, UUID tenantId, MerchantPermission... required) {
    MerchantAccess a = requireMember(principal, tenantId);
    for (MerchantPermission p : required) {
      if (!a.has(p)) throw new IllegalArgumentException(PERMISSION_DENIED);
    }
    return a;
  }

  /** Caller must hold at least one of the listed permissions (read-only views shared by several pages). */
  public MerchantAccess requireAny(ApiUserPrincipal principal, UUID tenantId, MerchantPermission... anyOf) {
    MerchantAccess a = requireMember(principal, tenantId);
    if (Arrays.stream(anyOf).noneMatch(a::has)) throw new IllegalArgumentException(PERMISSION_DENIED);
    return a;
  }

  /** Throws {@code permission_denied} unless the already-resolved access holds the permission. */
  public static void check(MerchantAccess access, MerchantPermission p) {
    if (!access.has(p)) throw new IllegalArgumentException(PERMISSION_DENIED);
  }

  /** The caller's own effective access, for the admin UI. */
  public Map<String, Object> describe(MerchantAccess a) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("isOwner", a.owner());
    out.put("role", a.owner() ? Role.MERCHANT_OWNER.name() : Role.MERCHANT_STAFF.name());
    out.put("permissions", MerchantPermission.names(a.permissions()));
    out.putAll(catalogue());
    return out;
  }

  /** Owner view of one staff member's stored permissions. */
  public Map<String, Object> staffPermissions(UUID tenantId, UUID employeeId) {
    return toStaffPermissionsView(requireEmployee(tenantId, employeeId));
  }

  /**
   * Owner replaces one staff member's permissions. {@code resetToDefault} stores NULL again (today's
   * staff access); otherwise exactly the given names are stored.
   */
  @Transactional
  public Map<String, Object> updateStaffPermissions(
      UUID tenantId, UUID employeeId, Collection<String> names, boolean resetToDefault) {
    EmployeeEntity emp = requireEmployee(tenantId, employeeId);
    emp.merchantPermissions =
        resetToDefault ? null : MerchantPermission.toCsv(MerchantPermission.parseStrict(names));
    employees.save(emp);
    return toStaffPermissionsView(emp);
  }

  private EmployeeEntity requireEmployee(UUID tenantId, UUID employeeId) {
    return employees
        .findById(employeeId)
        .filter(e -> tenantId.equals(e.tenantId))
        .orElseThrow(() -> new IllegalArgumentException("employee_not_found"));
  }

  private static Map<String, Object> toStaffPermissionsView(EmployeeEntity emp) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("employeeId", emp.id.toString());
    out.put("permissions", MerchantPermission.names(MerchantPermission.resolveStored(emp.merchantPermissions)));
    out.put("usingDefault", emp.merchantPermissions == null);
    out.putAll(catalogue());
    return out;
  }

  private static Map<String, Object> catalogue() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("areas", MerchantPermission.names(MerchantPermission.AREAS));
    out.put("riskyActions", MerchantPermission.names(MerchantPermission.RISKY_ACTIONS));
    out.put("defaultPermissions", MerchantPermission.names(MerchantPermission.LEGACY_STAFF_DEFAULT));
    out.put("ownerOnly", OWNER_ONLY_ACTIONS);
    return out;
  }
}
