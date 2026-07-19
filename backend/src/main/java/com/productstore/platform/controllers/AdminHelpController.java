package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.PlatformHelpContactService;
import com.productstore.platform.services.SupportTicketService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/help")
public class AdminHelpController {
  private final SupportTicketService tickets;
  private final PlatformHelpContactService helpContact;
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;

  public AdminHelpController(
      SupportTicketService tickets,
      PlatformHelpContactService helpContact,
      TenantAccessService tenantAccess,
      MembershipRepository memberships) {
    this.tickets = tickets;
    this.helpContact = helpContact;
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
  }

  @GetMapping("/contact")
  public Map<String, Object> contact(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    return helpContact.get();
  }

  @GetMapping("/tickets")
  public Map<String, Object> listTickets(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    return Map.of("tickets", tickets.listForTenant(tenant.id()));
  }

  @PostMapping("/tickets")
  public Map<String, Object> createTicket(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    String subject = body != null && body.get("subject") != null ? String.valueOf(body.get("subject")) : "";
    String message = body != null && body.get("body") != null ? String.valueOf(body.get("body")) : "";
    return tickets.createForMerchant(tenant.id(), principal.userId(), subject, message);
  }

  private void requireMerchantAccess(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}
