package com.productstore.platform.services;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
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

  public SupportConsoleService(
      TenantRepository tenants,
      UserRepository users,
      MembershipRepository memberships,
      OrderRepository orders,
      ProductRepository products,
      SalonBookingRepository salonBookings,
      SalonServiceRepository salonServices,
      SalonStaffRepository salonStaff,
      MerchantProvisioningService merchantProvisioning) {
    this.tenants = tenants;
    this.users = users;
    this.memberships = memberships;
    this.orders = orders;
    this.products = products;
    this.salonBookings = salonBookings;
    this.salonServices = salonServices;
    this.salonStaff = salonStaff;
    this.merchantProvisioning = merchantProvisioning;
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
                salonBookings.countByStatus(
                    com.productstore.platform.entities.SalonBookingEntity.Status.confirmed),
            "servicesActiveAcrossTenants", salonServices.countAllActive(),
            "staffActiveAcrossTenants", salonStaff.countAllActive());

    return Map.of(
        "counts",
            Map.of(
                "tenants", tenants.count(),
                "users", users.count(),
                "merchantStaffMembershipRows", memberships.countByRoleIn(merchantMembershipRoles),
                "tenantsWithMerchantMembership",
                    memberships.countDistinctTenantsHavingMerchantMembership(merchantMembershipRoles),
                "productsActive", products.countActiveAll()),
        "orders", ordersAgg,
        "salon", salonsAgg,
        "revenue", Map.of("paidOrdersTotalZar", revenuePaidTotalZar.toPlainString()),
        "platformRoles",
            Map.of(
                "supportUsers",
                    memberships.countByRole(Role.SUPPORT_USER),
                "platformAdmins",
                    memberships.countByRole(Role.PLATFORM_ADMIN)));
  }

  public List<Map<String, Object>> listMerchants(String q) {
    String needle = normalizeQuery(q);
    return tenants.searchMerchants(needle).stream().map(this::merchantSummary).toList();
  }

  @Transactional
  public Map<String, Object> createMerchant(
      String merchantName, String merchantSlugRaw, String ownerEmail, String ownerPassword) {
    var reg =
        merchantProvisioning.registerMerchant(merchantName, merchantSlugRaw, ownerEmail, ownerPassword);
    return merchantDetail(reg.tenant().slug);
  }

  @Transactional
  public Map<String, Object> updateMerchant(String currentSlugRaw, String nameRaw, String newSlugRaw) {
    String cur = String.valueOf(currentSlugRaw == null ? "" : currentSlugRaw).trim();
    if (cur.isEmpty()) throw new IllegalArgumentException("slug_required");
    TenantEntity t =
        tenants.findBySlug(cur).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));

    boolean hasName = nameRaw != null && !nameRaw.trim().isEmpty();
    boolean hasSlug = newSlugRaw != null && !newSlugRaw.trim().isEmpty();
    if (!hasName && !hasSlug) {
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
    tenants.save(t);
    return merchantDetail(t.slug);
  }

  @Transactional
  public void deleteMerchant(String slugRaw) {
    String slug = String.valueOf(slugRaw == null ? "" : slugRaw).trim();
    if (slug.isEmpty()) throw new IllegalArgumentException("slug_required");
    TenantEntity t =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("merchant_not_found"));
    tenants.delete(t);
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
                salonBookings.countByTenantIdAndStatus(tid, com.productstore.platform.entities.SalonBookingEntity.Status.confirmed),
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
    m.put(
        "links",
        Map.of("storefrontPath", "/m/" + t.slug, "adminPath", "/m/" + t.slug + "/admin"));
    m.put("revenue", Map.of("paidOrdersTotalZar", revenuePaidTotalZar.toPlainString()));
    return m;
  }

  private static String normalizeQuery(String q) {
    if (q == null) return null;
    String s = q.trim();
    return s.isEmpty() ? null : s;
  }
}
