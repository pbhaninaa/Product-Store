package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Single-row platform bank details where merchants pay Product-Store subscriptions. */
@Entity
@Table(name = "platform_banking")
public class PlatformBankingEntity {
  @Id
  public UUID id;

  @Column(name = "bank_name", length = 120)
  public String bankName;

  @Column(name = "account_name", length = 160)
  public String accountName;

  @Column(name = "account_number", length = 64)
  public String accountNumber;

  @Column(name = "branch_code", length = 32)
  public String branchCode;

  @Column(name = "reference_hint", length = 255)
  public String referenceHint;

  /** Optional payment URL (shown to merchants for online/pay-link remittance). */
  @Column(name = "payment_link", length = 500)
  public String paymentLink;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }
}
