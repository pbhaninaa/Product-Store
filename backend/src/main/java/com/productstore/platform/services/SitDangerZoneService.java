package com.productstore.platform.services;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.repositories.SupportTicketRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-admin database reset: removes merchants, staff, support users, and related data.
 * Keeps only the acting system admin's user credentials + PLATFORM_ADMIN membership, plus
 * platform config (plans, banking, features, help-contact).
 */
@Service
public class SitDangerZoneService {
  public static final String CONFIRM_PHRASE = "RESET_DATABASE";

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
  private final SupportAuditLogRepository auditLogs;
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
      SupportAuditLogRepository auditLogs,
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
    this.auditLogs = auditLogs;
    this.audit = audit;
  }

  public Map<String, Object> status() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("available", true);
    m.put("profiles", Arrays.asList(environment.getActiveProfiles()));
    m.put("confirmPhrase", CONFIRM_PHRASE);
    m.put(
        "keeps",
        "Only your system-admin login. Support users, merchants, and store data are removed.");
    return m;
  }

  @Transactional
  public Map<String, Object> wipeMerchantData(ApiUserPrincipal actor, String confirm) {
    if (actor == null || actor.userId() == null) {
      throw new IllegalArgumentException("not_authenticated");
    }
    if (!CONFIRM_PHRASE.equals(String.valueOf(confirm == null ? "" : confirm).trim())) {
      throw new IllegalArgumentException("confirm_required");
    }

    UUID keepUserId = actor.userId();
    UserEntity keepUser =
        users.findById(keepUserId).orElseThrow(() -> new IllegalStateException("admin_missing"));

    int tenantCount = (int) tenants.count();
    int userCountBefore = (int) users.count();

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
    auditLogs.deleteAll();

    for (TenantEntity t : List.copyOf(tenants.findAll())) {
      tenants.delete(t);
    }

    for (MembershipEntity m : List.copyOf(memberships.findAll())) {
      if (!keepUserId.equals(m.userId)) {
        memberships.delete(m);
      } else if (m.role != Role.PLATFORM_ADMIN) {
        memberships.delete(m);
      }
    }

    for (UserEntity u : List.copyOf(users.findAll())) {
      if (!keepUserId.equals(u.id)) {
        users.delete(u);
      }
    }

    // Ensure the remaining admin still has PLATFORM_ADMIN (in case it was only on a deleted row).
    boolean hasAdmin =
        memberships.findAllByUserId(keepUserId).stream()
            .anyMatch(m -> m.role == Role.PLATFORM_ADMIN);
    if (!hasAdmin) {
      MembershipEntity m = new MembershipEntity();
      m.id = UUID.randomUUID();
      m.userId = keepUserId;
      m.tenantId = null;
      m.role = Role.PLATFORM_ADMIN;
      m.createdAt = java.time.Instant.now();
      memberships.save(m);
    }

    audit.record(
        actor,
        "DANGER_RESET_DATABASE",
        "PLATFORM",
        "db-reset",
        "removedTenants="
            + tenantCount
            + ";removedUsers="
            + Math.max(0, userCountBefore - 1)
            + ";kept="
            + keepUser.email);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("removedTenants", tenantCount);
    out.put("remainingUsers", users.count());
    out.put("keptEmail", keepUser.email);
    return out;
  }
}
