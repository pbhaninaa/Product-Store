package com.productstore.platform.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.productstore.platform.constants.SubscriptionPaymentProofStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant_subscriptions")
public class MerchantSubscriptionEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  public UUID tenantId;

  /** Selected / target tier (may differ from billed while upgrade pending). */
  @Enumerated(EnumType.STRING)
  @Column(name = "plan_tier", length = 16)
  public TenantEntity.SubscriptionPlan planTier;

  /** Last paid/confirmed tier - drives features while period is valid. */
  @Enumerated(EnumType.STRING)
  @Column(name = "billed_plan_tier", length = 16)
  public TenantEntity.SubscriptionPlan billedPlanTier;

  @Column(nullable = false)
  public boolean active = false;

  @Column(name = "period_start")
  public LocalDate periodStart;

  @Column(name = "period_end")
  public LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_proof_status", length = 16)
  public SubscriptionPaymentProofStatus paymentProofStatus = SubscriptionPaymentProofStatus.NONE;

  @Column(name = "payment_proof_original_filename", length = 255)
  public String paymentProofOriginalFilename;

  @Column(name = "payment_proof_relative_path", length = 500)
  public String paymentProofRelativePath;

  @Column(name = "payment_proof_uploaded_at")
  public Instant paymentProofUploadedAt;

  @Column(name = "payment_proof_reviewed_at")
  public Instant paymentProofReviewedAt;

  @Column(name = "payment_proof_rejection_note", length = 1000)
  public String paymentProofRejectionNote;

  @Column(name = "payment_proof_expected_fee")
  public Double paymentProofExpectedFee;

  @Column(name = "payment_proof_auto_passed")
  public Boolean paymentProofAutoPassed;

  @Column(name = "payment_proof_auto_summary", length = 2000)
  public String paymentProofAutoSummary;

  @Column(name = "mandatory_payment_reference", length = 64)
  public String mandatoryPaymentReference;

  @Column(name = "payment_reference_generated_at")
  public Instant paymentReferenceGeneratedAt;

  /** Peach Hosted Checkout V2 checkout id for the pending billing-period payment. */
  @Column(name = "peach_checkout_id", length = 128)
  public String peachCheckoutId;

  /** Peach merchantTransactionId (8–16 chars) for webhook matching. */
  @Column(name = "peach_merchant_transaction_id", length = 64)
  public String peachMerchantTransactionId;

  /** Selected rail for the pending/latest Peach subscription checkout. */
  @Enumerated(EnumType.STRING)
  @Column(name = "peach_payment_method", length = 16)
  public PeachPaymentMethod peachPaymentMethod;

  /** True once the merchant has consumed their one free first billing period. */
  @Column(name = "trial_used", nullable = false)
  public boolean trialUsed = false;

  /** True while the current active period was started as a free trial (no EFT). */
  @Column(name = "on_trial", nullable = false)
  public boolean onTrial = false;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (updatedAt == null) updatedAt = Instant.now();
    if (paymentProofStatus == null) paymentProofStatus = SubscriptionPaymentProofStatus.NONE;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }
}
