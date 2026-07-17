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
 * Immutable-amount Peach Hosted Checkout attempt for merchant subscription billing. One row per
 * checkout; verified callbacks settle via unique merchantTransactionId.
 */
@Entity
@Table(
    name = "subscription_peach_payments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_subscription_peach_merchant_tx",
          columnNames = "peach_merchant_transaction_id")
    },
    indexes = {
      @Index(name = "idx_subscription_peach_tenant_status", columnList = "tenant_id,status"),
      @Index(name = "idx_subscription_peach_checkout", columnList = "peach_checkout_id")
    })
public class SubscriptionPeachPaymentEntity {
  public static final String STATUS_PENDING = "pending_peach";
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
  @Column(name = "peach_payment_method", length = 16, nullable = false)
  public PeachPaymentMethod peachPaymentMethod;

  @Column(name = "peach_merchant_transaction_id", length = 64, nullable = false, unique = true)
  public String peachMerchantTransactionId;

  @Column(name = "peach_checkout_id", length = 128)
  public String peachCheckoutId;

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
