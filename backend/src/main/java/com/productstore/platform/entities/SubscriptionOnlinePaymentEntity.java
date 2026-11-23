package com.productstore.platform.entities;

import java.math.BigDecimal;
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
import jakarta.persistence.UniqueConstraint;

/**
 * One online checkout attempt for merchant subscription billing, with the amount fixed when the
 * checkout starts. Verified payment notifications settle it through its unique payment reference.
 */
@Entity
@Table(
    name = "subscription_online_payments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_subscription_online_merchant_tx",
          columnNames = "online_payment_reference")
    },
    indexes = {
      @Index(name = "idx_subscription_online_tenant_status", columnList = "tenant_id,status"),
      @Index(name = "idx_subscription_online_checkout", columnList = "online_payment_provider_id")
    })
public class SubscriptionOnlinePaymentEntity {
  public static final String STATUS_PENDING = "pending_online";
  public static final String STATUS_COMPLETED = "completed";

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "plan_tier", length = 16, nullable = false)
  public TenantEntity.SubscriptionPlan planTier;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal amount;

  @Column(length = 8, nullable = false)
  public String currency = "ZAR";

  @Column(length = 24, nullable = false)
  public String status = STATUS_PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "online_payment_channel", length = 16, nullable = false)
  public OnlinePaymentChannel onlinePaymentChannel;

  /** Our reference sent to the payment provider; unique per checkout attempt. */
  @Column(name = "online_payment_reference", length = 64, nullable = false)
  public String onlinePaymentReference;

  /** The provider's own transaction id, stored once the payment is confirmed. */
  @Column(name = "online_payment_provider_id", length = 128)
  public String onlinePaymentProviderId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (currency == null || currency.isBlank()) currency = "ZAR";
    if (status == null || status.isBlank()) status = STATUS_PENDING;
  }
}
