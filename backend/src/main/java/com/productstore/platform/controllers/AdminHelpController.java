package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.PlatformHelpContactService;
import com.productstore.platform.services.SupportTicketService;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

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
  private final MerchantAccessService access;

  public AdminHelpController(
      SupportTicketService tickets,
      PlatformHelpContactService helpContact,
      TenantAccessService tenantAccess,
      MerchantAccessService access) {
    this.tickets = tickets;
    this.helpContact = helpContact;
    this.tenantAccess = tenantAccess;
    this.access = access;
  }

  @GetMapping("/contact")
  public Map<String, Object> contact(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return helpContact.get();
  }

  @GetMapping("/tickets")
  public Map<String, Object> listTickets(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return Map.of("tickets", tickets.listForTenant(tenant.id()));
  }

  @PostMapping("/tickets")
  public Map<String, Object> createTicket(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    String subject = body != null && body.get("subject") != null ? String.valueOf(body.get("subject")) : "";
    String message = body != null && body.get("body") != null ? String.valueOf(body.get("body")) : "";
    return tickets.createForMerchant(tenant.id(), principal.userId(), subject, message);
  }
}
