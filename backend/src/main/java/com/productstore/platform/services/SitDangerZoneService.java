package com.productstore.platform.services;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeePayrollJobMarkRepository;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.InAppNotificationRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SupportTicketRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SIT/local/test-only wipe of merchant tenant data. Keeps platform admins, support users, plan
 * pricing, banking, feature flags, and help-contact config.
 */
@Service
public class SitDangerZoneService {
  private final Environment environment;
  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final ProductRepository products;
  private final SalonBookingRepository salonBookings;
  private final SalonServiceRepository salonServices;
  private final SalonStaffRepository salonStaff;
  private final SalonStaffAvailabilityRepository salonStaffAvailability;
  private final ShopSettingsRepository shopSettings;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final SupportTicketRepository supportTickets;
  private final EmployeeRepository employees;
  private final EmployeePayrollJobMarkRepository payrollMarks;
  private final InAppNotificationRepository notifications;
  private final SupportAuditService audit;

  public SitDangerZoneService(
      Environment environment,
      TenantRepository tenants,
      MembershipRepository memberships,
      UserRepository users,
      OrderRepository orders,
      OrderItemRepository orderItems,
      ProductRepository products,
      SalonBookingRepository salonBookings,
      SalonServiceRepository salonServices,
      SalonStaffRepository salonStaff,
      SalonStaffAvailabilityRepository salonStaffAvailability,
      ShopSettingsRepository shopSettings,
      MerchantSubscriptionRepository merchantSubscriptions,
      SupportTicketRepository supportTickets,
      EmployeeRepository employees,
      EmployeePayrollJobMarkRepository payrollMarks,
      InAppNotificationRepository notifications,
      SupportAuditService audit) {
    this.environment = environment;
    this.tenants = tenants;
    this.memberships = memberships;
    this.users = users;
    this.orders = orders;
    this.orderItems = orderItems;
    this.products = products;
    this.salonBookings = salonBookings;
    this.salonServices = salonServices;
    this.salonStaff = salonStaff;
    this.salonStaffAvailability = salonStaffAvailability;
    this.shopSettings = shopSettings;
    this.merchantSubscriptions = merchantSubscriptions;
    this.supportTickets = supportTickets;
    this.employees = employees;
    this.payrollMarks = payrollMarks;
    this.notifications = notifications;
    this.audit = audit;
  }

  public Map<String, Object> status() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("available", isDangerProfile());
    m.put("profiles", Arrays.asList(environment.getActiveProfiles()));
    m.put("confirmPhrase", "WIPE_MERCHANTS");
    return m;
  }

  public boolean isDangerProfile() {
    return Arrays.stream(environment.getActiveProfiles())
        .anyMatch(
            p ->
                "sit".equalsIgnoreCase(p)
                    || "local".equalsIgnoreCase(p)
                    || "test".equalsIgnoreCase(p));
  }

  @Transactional
  public Map<String, Object> wipeMerchantData(ApiUserPrincipal actor, String confirm) {
    if (!isDangerProfile()) {
      throw new IllegalStateException("danger_zone_unavailable");
    }
    if (!"WIPE_MERCHANTS".equals(String.valueOf(confirm == null ? "" : confirm).trim())) {
      throw new IllegalArgumentException("confirm_required");
    }

    Set<UUID> platformUserIds = new HashSet<>();
    for (MembershipEntity m :
        memberships.findAllByRoleIn(List.of(Role.SUPPORT_USER, Role.PLATFORM_ADMIN))) {
      platformUserIds.add(m.userId);
    }

    int tenantCount = (int) tenants.count();
    payrollMarks.deleteAll();
    employees.deleteAll();
    orderItems.deleteAll();
    orders.deleteAll();
    salonBookings.deleteAll();
    salonStaffAvailability.deleteAll();
    salonStaff.deleteAll();
    salonServices.deleteAll();
    products.deleteAll();
    shopSettings.deleteAll();
    merchantSubscriptions.deleteAll();
    supportTickets.deleteAll();
    notifications.deleteAll();

    for (MembershipEntity m : List.copyOf(memberships.findAll())) {
      if (m.role == Role.MERCHANT_OWNER || m.role == Role.MERCHANT_STAFF) {
        memberships.delete(m);
      }
    }

    for (TenantEntity t : List.copyOf(tenants.findAll())) {
      tenants.delete(t);
    }

    for (UserEntity u : List.copyOf(users.findAll())) {
      if (!platformUserIds.contains(u.id)) {
        users.delete(u);
      }
    }

    audit.record(
        actor,
        "DANGER_WIPE_MERCHANTS",
        "PLATFORM",
        "sit-danger",
        "removedTenants=" + tenantCount);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("removedTenants", tenantCount);
    out.put("remainingUsers", users.count());
    return out;
  }
}
