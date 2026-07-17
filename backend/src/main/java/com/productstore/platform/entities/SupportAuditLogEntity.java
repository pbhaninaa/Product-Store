package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "support_audit_logs",
    indexes = {@Index(name = "idx_support_audit_created", columnList = "created_at")})
public class SupportAuditLogEntity {
  @Id
  public UUID id;

  @Column(name = "actor_user_id")
  public UUID actorUserId;

  @Column(name = "actor_email", length = 320)
  public String actorEmail;

  @Column(nullable = false, length = 80)
  public String action;

  @Column(name = "entity_type", length = 40)
  public String entityType;

  @Column(name = "entity_id", length = 80)
  public String entityId;

  @Column(length = 2000)
  public String detail;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
