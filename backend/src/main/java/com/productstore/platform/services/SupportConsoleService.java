package com.productstore.platform.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.constants.SubscriptionPaymentProofStatus;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportConsoleService {
  private final TenantRepository tenants;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final OrderRepository orders;
  private final ProductRepository products;
  private final SalonBookingRepository salonBookings;
  private final SalonServiceRepository salonServices;
  private final SalonStaffRepository salonStaff;
  private final MerchantProvisioningService merchantProvisioning;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final SupportTicketService tickets;
  private final MerchantSubscriptionService subscriptionService;
  private final PasswordHasher passwordHasher;
  private final SupportAuditService audit;

  public SupportConsoleService(
      TenantRepository tenants,
      UserRepository users,
      MembershipRepository memberships,
      OrderRepository orders,
      ProductRepository products,
      SalonBookingRepository salonBookings,
      SalonServiceRepository salonServices,
      SalonStaffRepository salonStaff,
      MerchantProvisioningService merchantProvisioning,
      MerchantSubscriptionRepository merchantSubscriptions,
      SupportTicketService tickets,
      MerchantSubscriptionService subscriptionService,
      PasswordHasher passwordHasher,
      SupportAuditService audit) {
    this.tenants = tenants;
    this.users = users;
    this.memberships = memberships;
    this.orders = orders;
    this.products = products;
    this.salonBookings = salonBookings;
    this.salonServices = salonServices;
    this.salonStaff = salonStaff;
    this.merchantProvisioning = merchantProvisioning;
    this.merchantSubscriptions = merchantSubscriptions;
    this.tickets = tickets;
    this.subscriptionService = subscriptionService;
    this.passwordHasher = passwordHasher;
    this.audit = audit;
  }

  public Map<String, Object> overview() {
    var merchantMembershipRoles = List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF);
    BigDecimal revenuePaidTotalZar = orders.sumPaidTotalZarAll();

    Map<String, Object> ordersAgg =
        Map.of(
            "total", orders.count(),
            "paid", orders.countByStatus(OrderEntity.OrderStatus.paid),
            "pendingPayment", orders.countByStatus(OrderEntity.OrderStatus.pending_payment),
            "cancelled", orders.countByStatus(OrderEntity.OrderStatus.cancelled));

    Map<String, Object> salonsAgg =
        Map.of(
            "bookingsTotal", salonBookings.count(),
            "bookingsConfirmed",
                salonBookings.countByStatus(SalonBookingEntity.Status.confirmed),
            "servicesActiveAcrossTenants", salonServices.countAllActive(),
            "staffActiveAcrossTenants", salonStaff.countAllActive());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put(
        "counts",
        Map.of(
            "tenants",
            tenants.count(),
            "users",
            users.count(),
            "merchantStaffMembershipRows",
            memberships.countByRoleIn(merchantMembershipRoles),
            "tenantsWithMerchantMembership",
            memberships.countDistinctTenantsHavingMerchantMembership(merchantMembershipRoles),
            "productsActive",
            products.countActiveAll()));
    out.put("orders", ordersAgg);
    out.put("salon", salonsAgg);
    out.put("revenue", Map.of("paidOrdersTotalZar", revenuePaidTotalZar.toPlainString()));
    out.put(
        "platformRoles",
        Map.of(
            "supportUsers",
            memberships.countByRole(Role.SUPPORT_USER),
            "platformAdmins",
            memberships.countByRole(Role.PLATFORM_ADMIN)));
    out.put(
        "billing",
        Map.of(
            "pendingProofs",
            merchantSubscriptions.countByPaymentProofStatus(SubscriptionPaymentProofStatus.PENDING),
            "bankingConfigured",
            Boolean.TRUE.equals(subscriptionService.getPlatformBanking().get("configured"))));
    out.put("tickets", Map.of("open", tickets.openCount()));
    return out;
  }

  public List<Map<String, Object>> listMerchants(String q) {
    String needle = normalizeQuery(q);
    return tenants.searchMerchants(needle).stream().map(this::merchantSummary).toList();
  }

  @Transactional
  public Map<String, Object> createMerchant(
      String merchantName,
      String merchantSlugRaw,
      String ownerEmail,
      String ownerPassword,
      String subscriptionPlanRaw) {
    var reg =
        merchantProvisioning.registerMerchant(merchantName, merchantSlugRaw, ownerEmail, ownerPassword);
    TenantEntity tenant = reg.tenant();
    TenantEntity.SubscriptionPlan plan = parsePlan(subscriptionPlanRaw);
    if (plan != null && plan != TenantEntity.SubscriptionPlan.STARTER) {
      tenant.subscriptionPlan = plan;
      tenants.save(tenant);
    }
    return merchantDetail(reg.tenant().slug);
  }

  @Transactional
  public Map<String, Object> updateMerchant(
      String currentSlugRaw, String nameRaw, String newSlugRaw, String subscriptionPlanRaw) {
    String cur = String.valueOf(currentSlugRaw == null ? "" : currentSlugRaw).trim();
    if (cur.isEmpty()) throw new IllegalArgumentException("slug_required");
    TenantEntity t =
        tenants.findBySlug(cur).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));

    boolean hasName = nameRaw != null && !nameRaw.trim().isEmpty();
    boolean hasSlug = newSlugRaw != null && !newSlugRaw.trim().isEmpty();
    TenantEntity.SubscriptionPlan plan = parsePlan(subscriptionPlanRaw);
    boolean hasPlan = plan != null;
    if (!hasName && !hasSlug && !hasPlan) {
      throw new IllegalArgumentException("no_updates");
    }
    if (hasName) {
      t.name = nameRaw.trim();
    }
    if (hasSlug) {
      String next = TenantSlugUtil.normalize(newSlugRaw);
      if (!next.equals(t.slug) && tenants.findBySlug(next).isPresent()) {
        throw new IllegalArgumentException("merchant_slug_taken");
      }
      t.slug = next;
    }
    if (hasPlan) {
      t.subscriptionPlan = plan;
    }
    tenants.save(t);
    return merchantDetail(t.slug);
  }

  @Transactional
  public void deleteMerchant(String slugRaw, ApiUserPrincipal actor) {
    String slug = String.valueOf(slugRaw == null ? "" : slugRaw).trim();
    if (slug.isEmpty()) throw new IllegalArgumentException("slug_required");
    TenantEntity t =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));
    UUID id = t.id;
    tenants.delete(t);
    audit.record(actor, "MERCHANT_DELETE", "TENANT", id.toString(), slug);
  }

  @Transactional
  public Map<String, Object> resetOwnerPassword(
      String slugRaw, String newPassword, ApiUserPrincipal actor) {
    String slug = String.valueOf(slugRaw == null ? "" : slugRaw).trim();
    if (slug.isEmpty()) throw new IllegalArgumentException("slug_required");
    if (newPassword == null || newPassword.length() < 8) {
      throw new IllegalArgumentException("password_too_short");
    }
    TenantEntity t =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));
    MembershipEntity ownerMem =
        memberships.findAllByTenantIdAndRole(t.id, Role.MERCHANT_OWNER).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("merchant_owner_missing"));
    UserEntity owner =
        users.findById(ownerMem.userId).orElseThrow(() -> new IllegalStateException("owner_missing"));
    owner.passwordHash = passwordHasher.hash(newPassword);
    users.save(owner);
    audit.record(actor, "MERCHANT_RESET_OWNER_PASSWORD", "TENANT", t.id.toString(), owner.email);
    return Map.of("ok", true, "email", owner.email);
  }

  public List<Map<String, Object>> recentOrders() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (OrderEntity o : orders.findTop50ByOrderByCreatedAtDesc()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", o.id.toString());
      row.put("tenantId", o.tenantId.toString());
      tenants
          .findById(o.tenantId)
          .ifPresent(
              ten -> {
                row.put("tenantSlug", ten.slug);
                row.put("tenantName", ten.name);
              });
      row.put("customerName", o.customerName);
      row.put("status", o.status != null ? o.status.name() : null);
      row.put("totalZar", o.totalZar != null ? o.totalZar.toPlainString() : null);
      row.put("createdAt", o.createdAt != null ? o.createdAt.toString() : null);
      out.add(row);
    }
    return out;
  }

  public List<Map<String, Object>> recentBookings() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (SalonBookingEntity b : salonBookings.findTop50ByOrderByStartAtDesc()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", b.id.toString());
      row.put("tenantId", b.tenantId.toString());
      tenants
          .findById(b.tenantId)
          .ifPresent(
              ten -> {
                row.put("tenantSlug", ten.slug);
                row.put("tenantName", ten.name);
              });
      row.put("customerName", b.customerName);
      row.put("status", b.status != null ? b.status.name() : null);
      row.put("startAt", b.startAt != null ? b.startAt.toString() : null);
      out.add(row);
    }
    return out;
  }

  public Map<String, Object> merchantDetail(String slugRaw) {
    String slug = String.valueOf(slugRaw == null ? "" : slugRaw).trim();
    if (slug.isEmpty()) throw new IllegalArgumentException("slug_required");

    TenantEntity t =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));

    UUID tid = t.id;

    BigDecimal revenuePaidTotalZar = orders.sumPaidTotalZarByTenant(tid);

    Map<String, Object> orderCounts =
        Map.of(
            "total", orders.countByTenantId(tid),
            "paid", orders.countByTenantIdAndStatus(tid, OrderEntity.OrderStatus.paid),
            "pendingPayment",
                orders.countByTenantIdAndStatus(tid, OrderEntity.OrderStatus.pending_payment),
            "cancelled", orders.countByTenantIdAndStatus(tid, OrderEntity.OrderStatus.cancelled));

    Map<String, Object> salonCounts =
        Map.of(
            "bookingsTotal", salonBookings.countByTenantId(tid),
            "bookingsConfirmed",
                salonBookings.countByTenantIdAndStatus(tid, SalonBookingEntity.Status.confirmed),
            "servicesActive", salonServices.countByTenantIdAndActiveTrue(tid),
            "staffActive", salonStaff.countByTenantIdAndActiveTrue(tid));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("merchant", merchantCore(t));
    out.put(
        "links",
        Map.of(
            "storefrontPath", "/m/" + t.slug,
            "adminPath", "/m/" + t.slug + "/admin"));
    out.put("orders", orderCounts);
    out.put(
        "products",
        Map.of("active", products.countActiveByTenant(tid), "all", products.countByTenantId(tid)));
    out.put("salon", salonCounts);
    out.put("revenue", Map.of("paidOrdersTotalZar", revenuePaidTotalZar.toPlainString()));
    return out;
  }

  private Map<String, Object> merchantCore(TenantEntity t) {
    return Map.of(
        "id",
        t.id.toString(),
        "slug",
        t.slug,
        "name",
        t.name,
        "subscriptionPlan",
        effectivePlan(t).name(),
        "createdAt",
        t.createdAt.toString());
  }

  private Map<String, Object> merchantSummary(TenantEntity t) {
    UUID tid = t.id;

    BigDecimal revenuePaidTotalZar = orders.sumPaidTotalZarByTenant(tid);

    Map<String, Object> m = new LinkedHashMap<>(merchantCore(t));
    m.put(
        "totals",
        Map.of(
            "orders", orders.countByTenantId(tid),
            "productsActive", products.countActiveByTenant(tid),
            "salonBookings", salonBookings.countByTenantId(tid)));
    m.put("revenuePaidTotalZar", revenuePaidTotalZar.toPlainString());
    return m;
  }

  private static String normalizeQuery(String q) {
    if (q == null) return "";
    return q.trim().toLowerCase();
  }

  private static TenantEntity.SubscriptionPlan parsePlan(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return TenantEntity.SubscriptionPlan.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_subscription_plan");
    }
  }

  private static TenantEntity.SubscriptionPlan effectivePlan(TenantEntity t) {
    return t.subscriptionPlan != null ? t.subscriptionPlan : TenantEntity.SubscriptionPlan.STARTER;
  }
}
