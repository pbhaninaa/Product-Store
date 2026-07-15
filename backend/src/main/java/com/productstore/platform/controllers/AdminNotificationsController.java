package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.InAppNotificationService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/notifications")
public class AdminNotificationsController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final InAppNotificationService notifications;

  public AdminNotificationsController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      InAppNotificationService notifications) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.notifications = notifications;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return Map.of("notifications", notifications.listForUser(principal.userId(), tenant.id()));
  }

  @GetMapping("/unread-count")
  public Map<String, Object> unreadCount(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return Map.of("count", notifications.unreadCount(principal.userId(), tenant.id()));
  }

  @PostMapping("/{notificationId}/read")
  public Map<String, Object> markRead(
      @PathVariable String merchantSlug,
      @PathVariable UUID notificationId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    boolean ok = notifications.markRead(principal.userId(), tenant.id(), notificationId);
    return Map.of("ok", ok);
  }

  @PostMapping("/read-all")
  public Map<String, Object> markAllRead(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    int n = notifications.markAllRead(principal.userId(), tenant.id());
    return Map.of("ok", true, "marked", n);
  }

  private void requireMerchant(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}
