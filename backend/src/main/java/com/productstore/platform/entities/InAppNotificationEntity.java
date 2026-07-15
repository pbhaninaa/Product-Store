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
    name = "in_app_notifications",
    indexes = {
      @Index(name = "idx_inapp_user_created", columnList = "user_id, created_at"),
      @Index(name = "idx_inapp_user_read", columnList = "user_id, is_read")
    })
public class InAppNotificationEntity {
  @Id
  public UUID id;

  @Column(name = "user_id", nullable = false)
  public UUID userId;

  @Column(name = "tenant_id")
  public UUID tenantId;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(nullable = false, length = 4000)
  public String body;

  @Column(name = "notification_type", nullable = false, length = 80)
  public String notificationType;

  @Column(name = "reference_type", length = 40)
  public String referenceType;

  @Column(name = "reference_id", length = 80)
  public String referenceId;

  @Column(name = "is_read", nullable = false)
  public boolean isRead = false;

  @Column(name = "read_at")
  public Instant readAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
