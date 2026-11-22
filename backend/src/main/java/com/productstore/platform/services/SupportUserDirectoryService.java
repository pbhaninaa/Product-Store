package com.productstore.platform.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-wide user directory for the Support console: search every account (clients, merchant
 * owners and staff, support users, platform admins), and suspend / reactivate or send a password
 * reset. Only directory fields are ever returned (no password hashes, tokens or payment data).
 */
@Service
public class SupportUserDirectoryService {
  public static final int DEFAULT_PAGE_SIZE = 25;
  public static final int MAX_PAGE_SIZE = 100;

  /** Display order of a user's roles (most privileged first). */
  private static final List<Role> ROLE_ORDER =
      List.of(Role.PLATFORM_ADMIN, Role.SUPPORT_USER, Role.MERCHANT_OWNER, Role.MERCHANT_STAFF, Role.CLIENT);

  /** Accounts only a platform admin may suspend or reset. */
  private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.PLATFORM_ADMIN, Role.SUPPORT_USER);

  private static final Set<Role> MERCHANT_ROLES = EnumSet.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF);

  public enum StatusFilter {
    ACTIVE,
    SUSPENDED
  }

  /** Search criteria; every field is optional. {@code page} is zero-based. */
  public record Filter(String q, String role, String store, String status, Integer page, Integer size) {}

  private final EntityManager em;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final TenantRepository tenants;
  private final SupportAccessService access;
  private final SupportAuditService audit;
  private final PasswordResetService passwordReset;

  public SupportUserDirectoryService(
      EntityManager em,
      UserRepository users,
      MembershipRepository memberships,
      TenantRepository tenants,
      SupportAccessService access,
      SupportAuditService audit,
      PasswordResetService passwordReset) {
    this.em = em;
    this.users = users;
    this.memberships = memberships;
    this.tenants = tenants;
    this.access = access;
    this.audit = audit;
    this.passwordReset = passwordReset;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> search(ApiUserPrincipal actor, Filter filter) {
    access.requirePermission(actor, SupportPermission.VIEW_USERS);
    Filter f = filter == null ? new Filter(null, null, null, null, null, null) : filter;
    int page = f.page() == null || f.page() < 0 ? 0 : f.page();
    int size =
        f.size() == null || f.size() < 1 ? DEFAULT_PAGE_SIZE : Math.min(f.size(), MAX_PAGE_SIZE);

    String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
    Role role = parseRole(f.role());
    StatusFilter status = parseStatus(f.status());
    UUID tenantId = null;
    String store = f.store() == null ? "" : f.store().trim();
    if (!store.isEmpty()) {
      TenantEntity t = tenants.findBySlug(store.toLowerCase(Locale.ROOT)).orElse(null);
      if (t == null) return pagePayload(List.of(), page, size, 0, actor);
      tenantId = t.id;
    }

    CriteriaBuilder cb = em.getCriteriaBuilder();

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<UserEntity> countRoot = countQuery.from(UserEntity.class);
    countQuery
        .select(cb.count(countRoot))
        .where(predicates(cb, countQuery, countRoot, q, role, status, tenantId));
    long total = em.createQuery(countQuery).getSingleResult();
    if (total == 0 || (long) page * size >= total) {
      return pagePayload(List.of(), page, size, total, actor);
    }

    CriteriaQuery<UserEntity> listQuery = cb.createQuery(UserEntity.class);
    Root<UserEntity> u = listQuery.from(UserEntity.class);
    listQuery
        .select(u)
        .where(predicates(cb, listQuery, u, q, role, status, tenantId))
        .orderBy(cb.desc(u.get("createdAt")), cb.asc(u.get("id")));
    List<UserEntity> rows =
        em.createQuery(listQuery).setFirstResult(page * size).setMaxResults(size).getResultList();
    return pagePayload(rows, page, size, total, actor);
  }

  /** Stores for the directory's store filter (name and slug only). */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> storeOptions(ApiUserPrincipal actor) {
    access.requirePermission(actor, SupportPermission.VIEW_USERS);
    return tenants.findAll().stream()
        .sorted(
            Comparator.comparing(
                (TenantEntity t) -> t.name == null ? "" : t.name.toLowerCase(Locale.ROOT)))
        .map(
            t -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("slug", t.slug);
              m.put("name", t.name == null ? "" : t.name);
              return m;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> setSuspended(ApiUserPrincipal actor, UUID userId, boolean suspended) {
    access.requirePermission(actor, SupportPermission.MANAGE_USERS);
    UserEntity user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (actor.userId().equals(userId)) throw new IllegalArgumentException("cannot_suspend_self");
    List<MembershipEntity> ms = memberships.findAllByUserId(userId);
    requireMayManage(actor, ms);
    if (user.suspended != suspended) {
      user.suspended = suspended;
      // Belt and braces: the JWT filter already rejects suspended accounts on every request, and
      // revoking also keeps tokens issued before the suspension dead after a later reactivation.
      if (suspended) user.revokeTokens();
      users.save(user);
    }
    audit.record(
        actor, suspended ? "USER_SUSPEND" : "USER_REACTIVATE", "USER", userId.toString(), user.email);
    return toRow(user, ms, tenantsById(ms), actor);
  }

  @Transactional
  public Map<String, Object> sendPasswordReset(ApiUserPrincipal actor, UUID userId) {
    access.requirePermission(actor, SupportPermission.MANAGE_USERS);
    UserEntity user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    requireMayManage(actor, memberships.findAllByUserId(userId));
    // The reset flow silently skips suspended accounts; say so instead of pretending it was sent.
    if (user.suspended) throw new IllegalArgumentException("account_suspended");
    passwordReset.requestReset(user.email);
    audit.record(actor, "USER_PASSWORD_RESET_SENT", "USER", userId.toString(), user.email);
    return Map.of("ok", true, "userId", userId.toString());
  }

  /** Support and platform-admin accounts can only be acted on by a platform admin. */
  private static void requireMayManage(ApiUserPrincipal actor, Collection<MembershipEntity> targetMemberships) {
    boolean targetIsStaff = targetMemberships.stream().anyMatch(m -> STAFF_ROLES.contains(m.role));
    if (targetIsStaff && !actor.roles().contains(Role.PLATFORM_ADMIN)) {
      throw new IllegalArgumentException("forbidden");
    }
  }

  private static Role parseRole(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_role");
    }
  }

  private static StatusFilter parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return StatusFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_status");
    }
  }

  private static Predicate[] predicates(
      CriteriaBuilder cb,
      AbstractQuery<?> query,
      Root<UserEntity> u,
      String q,
      Role role,
      StatusFilter status,
      UUID tenantId) {
    List<Predicate> out = new ArrayList<>();
    if (!q.isEmpty()) {
      String pattern = "%" + escapeLike(q) + "%";
      Expression<String> email = cb.lower(u.<String>get("email"));
      Expression<String> name = cb.lower(cb.coalesce(u.<String>get("displayName"), ""));
      out.add(cb.or(cb.like(email, pattern, '\\'), cb.like(name, pattern, '\\')));
    }
    if (status != null) {
      out.add(cb.equal(u.get("suspended"), status == StatusFilter.SUSPENDED));
    }
    if (role != null) {
      Subquery<UUID> sq = query.subquery(UUID.class);
      Root<MembershipEntity> m = sq.from(MembershipEntity.class);
      sq.select(m.get("id")).where(cb.equal(m.get("userId"), u.get("id")), cb.equal(m.get("role"), role));
      out.add(cb.exists(sq));
    }
    if (tenantId != null) {
      Subquery<UUID> sq = query.subquery(UUID.class);
      Root<MembershipEntity> m = sq.from(MembershipEntity.class);
      sq.select(m.get("id"))
          .where(cb.equal(m.get("userId"), u.get("id")), cb.equal(m.get("tenantId"), tenantId));
      out.add(cb.exists(sq));
    }
    return out.toArray(new Predicate[0]);
  }

  static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private Map<String, Object> pagePayload(
      List<UserEntity> rows, int page, int size, long total, ApiUserPrincipal actor) {
    List<UUID> ids = rows.stream().map(r -> r.id).toList();
    List<MembershipEntity> ms =
        ids.isEmpty()
            ? List.of()
            : em.createQuery("select m from MembershipEntity m where m.userId in :ids", MembershipEntity.class)
                .setParameter("ids", ids)
                .getResultList();
    Map<UUID, List<MembershipEntity>> byUser = ms.stream().collect(Collectors.groupingBy(m -> m.userId));
    Map<UUID, TenantEntity> tenantMap = tenantsById(ms);

    List<Map<String, Object>> items = new ArrayList<>();
    for (UserEntity u : rows) {
      items.add(toRow(u, byUser.getOrDefault(u.id, List.of()), tenantMap, actor));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("users", items);
    out.put("page", page);
    out.put("size", size);
    out.put("total", total);
    out.put("totalPages", total == 0 ? 0 : (int) ((total + size - 1) / size));
    return out;
  }

  private Map<UUID, TenantEntity> tenantsById(Collection<MembershipEntity> ms) {
    Set<UUID> tenantIds =
        ms.stream()
            .filter(m -> m.tenantId != null && MERCHANT_ROLES.contains(m.role))
            .map(m -> m.tenantId)
            .collect(Collectors.toSet());
    if (tenantIds.isEmpty()) return Map.of();
    return tenants.findAllById(tenantIds).stream()
        .collect(Collectors.toMap(t -> t.id, Function.identity()));
  }

  /** The only user fields the directory exposes. */
  private static Map<String, Object> toRow(
      UserEntity u, List<MembershipEntity> ms, Map<UUID, TenantEntity> tenantMap, ApiUserPrincipal actor) {
    Set<Role> held = ms.stream().map(m -> m.role).filter(r -> r != null).collect(Collectors.toSet());
    List<String> roles = ROLE_ORDER.stream().filter(held::contains).map(Enum::name).toList();

    List<Map<String, Object>> stores = new ArrayList<>();
    ms.stream()
        .filter(m -> m.tenantId != null && MERCHANT_ROLES.contains(m.role))
        .sorted(Comparator.comparing((MembershipEntity m) -> m.role == Role.MERCHANT_OWNER ? 0 : 1))
        .forEach(
            m -> {
              TenantEntity t = tenantMap.get(m.tenantId);
              if (t == null) return;
              Map<String, Object> s = new LinkedHashMap<>();
              s.put("slug", t.slug);
              s.put("name", t.name == null ? "" : t.name);
              s.put("role", m.role.name());
              stores.add(s);
            });

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", u.id.toString());
    row.put("name", u.displayName == null || u.displayName.isBlank() ? null : u.displayName);
    row.put("email", u.email);
    row.put("roles", roles);
    row.put("stores", stores);
    row.put("suspended", u.suspended);
    row.put("createdAt", u.createdAt != null ? u.createdAt.toString() : null);
    row.put("lastLoginAt", u.lastLoginAt != null ? u.lastLoginAt.toString() : null);
    row.put("self", actor != null && u.id.equals(actor.userId()));
    return row;
  }
}
