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
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One payment covered by a {@link ProviderPayoutEntity payout}, with a snapshot of its gross, captured fee
 * (null = unknown) and per-payment commission. {@code uk_provider_payout_lines_attempt} is the database
 * guarantee that a payment is in at most one payout, whatever the timing.
 */
@Entity
@Table(
    name = "provider_payout_lines",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_provider_payout_lines_attempt", columnNames = "payment_attempt_id"),
    indexes = {
      @Index(name = "idx_provider_payout_lines_payout", columnList = "payout_id"),
      @Index(name = "idx_provider_payout_lines_tenant_paid", columnList = "tenant_id,paid_at")
    })
public class ProviderPayoutLineEntity {

  @Id
  public UUID id;

  @Column(name = "payout_id", nullable = false)
  public UUID payoutId;

  @Column(name = "payment_attempt_id", nullable = false)
  public UUID paymentAttemptId;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  public OnlinePaymentAttemptEntity.Kind kind;

  @Column(name = "record_id", nullable = false)
  public UUID recordId;

  @Column(name = "payment_reference", nullable = false, length = 64)
  public String paymentReference;

  @Column(name = "paid_at", nullable = false)
  public Instant paidAt;

  @Column(name = "gross_cents", nullable = false)
  public long grossCents;

  /** Captured Paystack fee; null when it was unknown. */
  @Column(name = "fee_cents")
  public Long feeCents;

  @Column(name = "commission_cents", nullable = false)
  public long commissionCents;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }
}
