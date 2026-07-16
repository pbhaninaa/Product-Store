package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.services.InAppNotificationService;
import com.productstore.platform.services.PlatformFeatureService;
import com.productstore.platform.services.PlatformHelpContactService;
import com.productstore.platform.services.SupportAccessService;
import com.productstore.platform.services.SupportAuditService;
import com.productstore.platform.services.SupportStaffService;
import com.productstore.platform.services.SupportTicketService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportOpsController {
  private final SupportTicketService tickets;
  private final InAppNotificationService notifications;
  private final PlatformFeatureService features;
  private final SupportAuditService audit;
  private final SupportStaffService staff;
  private final PlatformHelpContactService helpContact;
  private final SupportAccessService access;

  public SupportOpsController(
      SupportTicketService tickets,
      InAppNotificationService notifications,
      PlatformFeatureService features,
      SupportAuditService audit,
      SupportStaffService staff,
      PlatformHelpContactService helpContact,
      SupportAccessService access) {
    this.tickets = tickets;
    this.notifications = notifications;
    this.features = features;
    this.audit = audit;
    this.staff = staff;
    this.helpContact = helpContact;
    this.access = access;
  }

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal ApiUserPrincipal principal) {
    return access.permissionsPayload(principal);
  }

  @GetMapping("/tickets")
  public Map<String, Object> listTickets(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(name = "status", required = false) String status) {
    access.requirePermission(principal, SupportPermission.MANAGE_TICKETS);
    return Map.of("tickets", tickets.listForSupport(status), "openCount", tickets.openCount());
  }

  @PostMapping("/tickets/{id}/resolve")
  public Map<String, Object> resolveTicket(
      @PathVariable UUID id,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    access.requirePermission(principal, SupportPermission.MANAGE_TICKETS);
    String note = body != null && body.get("note") != null ? String.valueOf(body.get("note")) : "";
    return tickets.resolve(id, principal, note);
  }

  @GetMapping("/notifications")
  public Map<String, Object> notifications(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requireSupport(principal);
    return Map.of(
        "notifications",
        notifications.listForPlatformUser(principal.userId()),
        "unreadCount",
        notifications.unreadCountPlatform(principal.userId()));
  }

  @PostMapping("/notifications/{id}/read")
  public Map<String, Object> markRead(
      @PathVariable UUID id, @AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requireSupport(principal);
    boolean ok = notifications.markRead(principal.userId(), null, id);
    return Map.of("ok", ok);
  }

  @PostMapping("/notifications/read-all")
  public Map<String, Object> markAllRead(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requireSupport(principal);
    return Map.of("marked", notifications.markAllReadPlatform(principal.userId()));
  }

  @GetMapping("/platform-features")
  public Map<String, Object> listFeatures(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_FEATURES);
    return Map.of("features", features.listOrBootstrap());
  }

  @PutMapping("/platform-features/{key}")
  public Map<String, Object> setFeature(
      @PathVariable String key,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    access.requirePermission(principal, SupportPermission.MANAGE_FEATURES);
    boolean enabled =
        body != null && body.get("enabled") != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
    Map<String, Object> out = features.setEnabled(key, enabled);
    audit.record(principal, "FEATURE_TOGGLE", "FEATURE", key, String.valueOf(enabled));
    return out;
  }

  @GetMapping("/audit")
  public Map<String, Object> audit(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.VIEW_AUDIT);
    return Map.of("entries", audit.listRecent());
  }

  @GetMapping("/staff")
  public Map<String, Object> listStaff(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.MANAGE_STAFF);
    return Map.of("staff", staff.listStaff());
  }

  @PostMapping("/staff")
  public Map<String, Object> createStaff(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody Map<String, Object> body) {
    String email = body != null && body.get("email") != null ? String.valueOf(body.get("email")) : "";
    String password =
        body != null && body.get("password") != null ? String.valueOf(body.get("password")) : "";
    @SuppressWarnings("unchecked")
    List<String> perms =
        body != null && body.get("permissions") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();
    return staff.createSupportUser(principal, email, password, perms);
  }

  @PostMapping("/staff/{userId}/suspend")
  public Map<String, Object> suspend(
      @PathVariable UUID userId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    return staff.setSuspended(principal, userId, true);
  }

  @PostMapping("/staff/{userId}/activate")
  public Map<String, Object> activate(
      @PathVariable UUID userId, @AuthenticationPrincipal ApiUserPrincipal principal) {
    return staff.setSuspended(principal, userId, false);
  }

  @PostMapping("/staff/{userId}/reset-password")
  public Map<String, Object> resetPassword(
      @PathVariable UUID userId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    String password =
        body != null && body.get("password") != null ? String.valueOf(body.get("password")) : "";
    return staff.resetPassword(principal, userId, password);
  }

  @PutMapping("/staff/{userId}/permissions")
  public Map<String, Object> updatePermissions(
      @PathVariable UUID userId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<String> perms =
        body != null && body.get("permissions") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();
    return staff.updatePermissions(principal, userId, perms);
  }

  @GetMapping("/help-contact")
  public Map<String, Object> getHelpContact(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requireSupport(principal);
    return helpContact.get();
  }

  @PutMapping("/help-contact")
  public Map<String, Object> putHelpContact(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody Map<String, Object> body) {
    access.requirePlatformAdmin(principal);
    Map<String, Object> out = helpContact.update(body);
    audit.record(principal, "HELP_CONTACT_UPDATE", "PLATFORM", "help-contact", null);
    return out;
  }
}
