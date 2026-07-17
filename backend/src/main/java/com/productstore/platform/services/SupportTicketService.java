package com.productstore.platform.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.SupportTicketEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.SupportTicketRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketService {
  private final SupportTicketRepository tickets;
  private final TenantRepository tenants;
  private final UserRepository users;
  private final InAppNotificationService notifications;
  private final SupportAuditService audit;

  public SupportTicketService(
      SupportTicketRepository tickets,
      TenantRepository tenants,
      UserRepository users,
      InAppNotificationService notifications,
      SupportAuditService audit) {
    this.tickets = tickets;
    this.tenants = tenants;
    this.users = users;
    this.notifications = notifications;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> createForMerchant(UUID tenantId, UUID userId, String subject, String body) {
    if (tenantId == null) throw new IllegalArgumentException("tenant_required");
    tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    String subj = subject == null ? "" : subject.trim();
    String b = body == null ? "" : body.trim();
    if (subj.isEmpty()) throw new IllegalArgumentException("subject_required");
    if (b.isEmpty()) throw new IllegalArgumentException("body_required");

    SupportTicketEntity t = new SupportTicketEntity();
    t.tenantId = tenantId;
    t.createdByUserId = userId;
    t.subject = subj.length() > 200 ? subj.substring(0, 200) : subj;
    t.body = b.length() > 4000 ? b.substring(0, 4000) : b;
    t.status = SupportTicketEntity.Status.OPEN;
    tickets.save(t);

    TenantEntity tenant = tenants.findById(tenantId).orElse(null);
    String name = tenant != null ? tenant.name : "Merchant";
    notifications.notifyPlatformStaff(
        "New help ticket",
        name + ": " + t.subject,
        "SUPPORT_TICKET",
        "TICKET",
        t.id.toString());
    return toMap(t);
  }

  public List<Map<String, Object>> listForSupport(String statusFilter) {
    List<SupportTicketEntity> rows;
    if (statusFilter != null && !statusFilter.isBlank()) {
      SupportTicketEntity.Status st =
          SupportTicketEntity.Status.valueOf(statusFilter.trim().toUpperCase());
      rows = tickets.findByStatusOrderByCreatedAtDesc(st);
    } else {
      rows = tickets.findTop100ByOrderByCreatedAtDesc();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (SupportTicketEntity t : rows) {
      out.add(toMap(t));
    }
    return out;
  }

  public List<Map<String, Object>> listForTenant(UUID tenantId) {
    return tickets.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toMap).toList();
  }

  @Transactional
  public Map<String, Object> resolve(UUID ticketId, ApiUserPrincipal actor, String note) {
    SupportTicketEntity t =
        tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
    if (t.status == SupportTicketEntity.Status.RESOLVED) {
      return toMap(t);
    }
    t.status = SupportTicketEntity.Status.RESOLVED;
    t.resolvedAt = Instant.now();
    t.resolvedByUserId = actor != null ? actor.userId() : null;
    t.resolutionNote = note == null ? "" : note.trim();
    tickets.save(t);
    audit.record(actor, "TICKET_RESOLVE", "TICKET", ticketId.toString(), t.resolutionNote);
    notifications.notifyUser(
        t.createdByUserId,
        t.tenantId,
        "Help ticket resolved",
        t.resolutionNote.isBlank() ? "Your support ticket was marked resolved." : t.resolutionNote,
        "SUPPORT_TICKET_RESOLVED",
        "TICKET",
        t.id.toString());
    return toMap(t);
  }

  public long openCount() {
    return tickets.countByStatus(SupportTicketEntity.Status.OPEN);
  }

  private Map<String, Object> toMap(SupportTicketEntity t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.id.toString());
    m.put("tenantId", t.tenantId.toString());
    tenants
        .findById(t.tenantId)
        .ifPresent(
            ten -> {
              m.put("tenantSlug", ten.slug);
              m.put("tenantName", ten.name);
            });
    m.put("createdByUserId", t.createdByUserId != null ? t.createdByUserId.toString() : null);
    if (t.createdByUserId != null) {
      users.findById(t.createdByUserId).ifPresent(u -> m.put("createdByEmail", u.email));
    }
    m.put("subject", t.subject);
    m.put("body", t.body);
    m.put("status", t.status.name());
    m.put("resolvedAt", t.resolvedAt != null ? t.resolvedAt.toString() : null);
    m.put("resolutionNote", t.resolutionNote);
    m.put("createdAt", t.createdAt != null ? t.createdAt.toString() : null);
    return m;
  }
}
