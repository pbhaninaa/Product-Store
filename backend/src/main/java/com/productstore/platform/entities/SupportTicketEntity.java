package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "support_tickets",
    indexes = {
      @Index(name = "idx_support_tickets_status", columnList = "status, created_at"),
      @Index(name = "idx_support_tickets_tenant", columnList = "tenant_id, created_at")
    })
public class SupportTicketEntity {
  public enum Status {
    OPEN,
    RESOLVED
  }

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "created_by_user_id", nullable = false)
  public UUID createdByUserId;

  @Column(nullable = false, length = 200)
  public String subject;

  @Column(nullable = false, length = 4000)
  public String body;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public Status status = Status.OPEN;

  @Column(name = "resolved_at")
  public Instant resolvedAt;

  @Column(name = "resolved_by_user_id")
  public UUID resolvedByUserId;

  @Column(name = "resolution_note", length = 2000)
  public String resolutionNote;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (status == null) status = Status.OPEN;
  }
}
