package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

/**
 * A payout the platform admin recorded after paying a merchant outside the app (the app never moves money).
 * Totals are the sums of its {@link ProviderPayoutLineEntity lines}; the commission rate is a snapshot, so a
 * later change never alters a released payout. All amounts are integer cents.
 *
 * <p>{@code feeCents} is the fee total used (captured, or adjusted by the admin); {@code capturedFeeCents}
 * the sum of the known Paystack fees. The check constraint keeps {@code net = gross - fee - commission}.
 * Payouts are never updated or deleted by the app, except by the test-database reset.
 */
@Entity
@Table(
    name = "provider_payouts",
    indexes = {
      @Index(name = "idx_provider_payouts_released", columnList = "released_at"),
      @Index(name = "idx_provider_payouts_tenant_released", columnList = "tenant_id,released_at")
    })
@Check(
    name = "ck_provider_payouts_amounts",
    constraints = "fee_cents >= 0 AND net_cents > 0 AND net_cents = gross_cents - fee_cents - commission_cents")
public class ProviderPayoutEntity {

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "payment_count", nullable = false)
  public int paymentCount;

  @Column(name = "gross_cents", nullable = false)
  public long grossCents;

  @Column(name = "captured_fee_cents", nullable = false)
  public long capturedFeeCents;

  @Column(name = "unknown_fee_count", nullable = false)
  public int unknownFeeCount;

  @Column(name = "fee_cents", nullable = false)
  public long feeCents;

  @Column(name = "fee_adjusted", nullable = false)
  public boolean feeAdjusted;

  @Column(name = "commission_basis_points", nullable = false)
  public int commissionBasisPoints;

  @Column(name = "commission_cents", nullable = false)
  public long commissionCents;

  @Column(name = "net_cents", nullable = false)
  public long netCents;

  @Column(nullable = false, length = 100)
  public String reference;

  /** Admin-internal; never shown to the merchant. */
  @Column(length = 500)
  public String note;

  @Column(name = "released_by_user_id", nullable = false)
  public UUID releasedByUserId;

  @Column(name = "released_by_email", nullable = false, length = 320)
  public String releasedByEmail;

  @Column(name = "released_at", nullable = false)
  public Instant releasedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (releasedAt == null) releasedAt = Instant.now();
  }
}
