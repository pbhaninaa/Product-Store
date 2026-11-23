package com.productstore.platform.services;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.productstore.platform.repositories.EmployeePayrollJobMarkRepository;
import com.productstore.platform.repositories.InAppNotificationRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.OrderReviewRepository;
import com.productstore.platform.repositories.PasswordResetTokenRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonBookingReviewRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.repositories.SupportTicketRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-admin test-database reset: deletes activity records (orders, bookings, payments, provider payouts
 * and their payment links, reviews, notifications, support tickets, payroll marks, password reset tokens,
 * audit log) and keeps everyone's accounts and set-up: users, roles, stores, shop settings, staff and
 * employee profiles, staff schedules, products, services, promotions, subscriptions, referrals and platform
 * configuration (including the platform commission).
 *
 * <p>Every repository this service injects is wiped; anything it does not inject is kept.
 * {@code SitDangerZoneGuardTest} fails when a new repository is neither wiped here nor listed as kept.
 */
@Service
public class SitDangerZoneService {
  public static final String CONFIRM_PHRASE = "RESET_DATABASE";

  private final Environment environment;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final SalonBookingRepository salonBookings;
  private final OnlinePaymentAttemptRepository onlinePaymentAttempts;
  private final ProviderPayoutLineRepository payoutLines;
  private final ProviderPayoutRepository payouts;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final OrderReviewRepository orderReviews;
  private final SalonBookingReviewRepository bookingReviews;
  private final InAppNotificationRepository notifications;
  private final SupportTicketRepository supportTickets;
  private final EmployeePayrollJobMarkRepository payrollMarks;
  private final PasswordResetTokenRepository passwordResetTokens;
  private final SupportAuditLogRepository auditLogs;
  private final SupportAuditService audit;

  public SitDangerZoneService(
      Environment environment,
      OrderRepository orders,
      OrderItemRepository orderItems,
      SalonBookingRepository salonBookings,
      OnlinePaymentAttemptRepository onlinePaymentAttempts,
      ProviderPayoutLineRepository payoutLines,
      ProviderPayoutRepository payouts,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      OrderReviewRepository orderReviews,
      SalonBookingReviewRepository bookingReviews,
      InAppNotificationRepository notifications,
      SupportTicketRepository supportTickets,
      EmployeePayrollJobMarkRepository payrollMarks,
      PasswordResetTokenRepository passwordResetTokens,
      SupportAuditLogRepository auditLogs,
      SupportAuditService audit) {
    this.environment = environment;
    this.orders = orders;
    this.orderItems = orderItems;
    this.salonBookings = salonBookings;
    this.onlinePaymentAttempts = onlinePaymentAttempts;
    this.payoutLines = payoutLines;
    this.payouts = payouts;
    this.subscriptionPayments = subscriptionPayments;
    this.orderReviews = orderReviews;
    this.bookingReviews = bookingReviews;
    this.notifications = notifications;
    this.supportTickets = supportTickets;
    this.payrollMarks = payrollMarks;
    this.passwordResetTokens = passwordResetTokens;
    this.auditLogs = auditLogs;
    this.audit = audit;
  }

  /** Only throwaway environments may wipe data; UAT, PROD and an unset profile are refused. */
  private static final List<String> WIPE_PROFILES = List.of("local", "sit", "test");

  boolean wipeAllowed() {
    return Arrays.stream(environment.getActiveProfiles())
        .map(p -> p.trim().toLowerCase())
        .anyMatch(WIPE_PROFILES::contains);
  }

  public Map<String, Object> status() {
    Map<String, Object> m = new LinkedHashMap<>();
    if (!wipeAllowed()) {
      m.put("available", false);
      return m;
    }
    m.put("available", true);
    m.put("profiles", Arrays.asList(environment.getActiveProfiles()));
    m.put("confirmPhrase", CONFIRM_PHRASE);
    m.put(
        "keeps",
        "All users, stores, staff, products, services, promotions, subscriptions and settings. Orders,"
            + " bookings, payments, payouts, reviews, notifications, tickets and logs are removed.");
    return m;
  }

  @Transactional
  public Map<String, Object> resetRecords(ApiUserPrincipal actor, String confirm) {
    if (actor == null || actor.userId() == null) {
      throw new IllegalArgumentException("not_authenticated");
    }
    if (!wipeAllowed()) {
      throw new IllegalStateException("danger_zone_disabled");
    }
    if (!CONFIRM_PHRASE.equals(String.valueOf(confirm == null ? "" : confirm).trim())) {
      throw new IllegalArgumentException("confirm_required");
    }

    long removedOrders = orders.count();
    long removedBookings = salonBookings.count();
    long removedPayments = onlinePaymentAttempts.count() + subscriptionPayments.count();

    // Payout lines reference payment attempts: remove them (and their payouts) first.
    payoutLines.deleteAll();
    payouts.deleteAll();
    onlinePaymentAttempts.deleteAll();
    subscriptionPayments.deleteAll();
    orderReviews.deleteAll();
    bookingReviews.deleteAll();
    payrollMarks.deleteAll();
    orderItems.deleteAll();
    orders.deleteAll();
    salonBookings.deleteAll();
    notifications.deleteAll();
    supportTickets.deleteAll();
    passwordResetTokens.deleteAll();
    auditLogs.deleteAll();

    audit.record(
        actor,
        "DANGER_RESET_DATABASE",
        "PLATFORM",
        "db-reset",
        "removedOrders="
            + removedOrders
            + ";removedBookings="
            + removedBookings
            + ";removedPayments="
            + removedPayments);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("removedOrders", removedOrders);
    out.put("removedBookings", removedBookings);
    out.put("removedPayments", removedPayments);
    return out;
  }
}
