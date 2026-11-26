package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The platform commission on in-app order and booking payments: one row with a fixed id, so two concurrent
 * first saves can never create two settings. The rate is in basis points (12.5% = 1250) so no amount is
 * ever a floating point number. Kept by the test-database reset (platform configuration).
 */
@Entity
@Table(name = "platform_commission_settings")
public class PlatformCommissionEntity {
  /** The only row's id. */
  public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

  @Id
  public UUID id;

  @Column(name = "commission_basis_points", nullable = false)
  public int commissionBasisPoints;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @Column(name = "updated_by_user_id")
  public UUID updatedByUserId;

  @Column(name = "automatic_provider_payouts")
  public Boolean automaticProviderPayouts;

  /** Days to wait after the customer paid before an automatic payout can be sent. Null means 7. */
  @Column(name = "automatic_provider_payout_delay_days")
  public Integer automaticProviderPayoutDelayDays;

  /**
   * ISO weekday (1 = Monday … 7 = Sunday) on which automatic payouts are sent. 0 or null = any day.
   */
  @Column(name = "automatic_provider_payout_weekday")
  public Integer automaticProviderPayoutWeekday;
}
