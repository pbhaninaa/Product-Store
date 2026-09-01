package com.productstore.platform.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "referrals",
    indexes = {
      @Index(name = "idx_referrer_id", columnList = "referrer_id"),
      @Index(name = "idx_referee_id", columnList = "referee_id"),
      @Index(name = "idx_has_subscribed", columnList = "has_subscribed"),
      @Index(name = "idx_commission_paid", columnList = "commission_paid")
    })
public class ReferralEntity {
  @Id
  public UUID id;

  @Column(name = "referrer_id", nullable = false)
  public UUID referrerId;

  @Column(name = "referee_id", nullable = false)
  public UUID refereeId;

  @Column(name = "referee_role", length = 64)
  public String refereeRole;

  @Column(name = "has_subscribed", nullable = false)
  public boolean hasSubscribed = false;

  @Column(name = "first_subscription_id")
  public UUID firstSubscriptionId;

  @Column(name = "subscribed_at")
  public Instant subscribedAt;

  @Column(name = "commission_amount", precision = 12, scale = 2)
  public BigDecimal commissionAmount;

  @Column(name = "commission_paid", nullable = false)
  public boolean commissionPaid = false;

  @Column(name = "commission_paid_at")
  public Instant commissionPaidAt;

  @Column(name = "commission_notes", length = 1000)
  public String commissionNotes;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }
}
