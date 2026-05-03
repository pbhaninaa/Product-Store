package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "salon_bookings")
public class SalonBookingEntity {
  public enum Status {
    pending,
    confirmed,
    cancelled
  }

  /** Mirrors {@link com.productstore.platform.entities.OrderEntity.PaymentMethod} naming for the storefront. */
  public enum ClientPaymentMethod {
    eft,
    cash_store
  }

  /** EFT proof pipeline: auto match reference → confirmed; else merchant review. */
  public enum PaymentVerificationState {
    not_applicable,
    awaiting_proof,
    auto_verified,
    manual_pending,
    manual_approved,
    manual_rejected
  }

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "service_id", nullable = false)
  public UUID serviceId;

  @Column(name = "staff_id", nullable = true)
  public UUID staffId;

  @Column(name = "customer_name", nullable = false)
  public String customerName;

  @Column(name = "customer_phone", nullable = false)
  public String customerPhone;

  @Column(name = "customer_email", nullable = false)
  public String customerEmail;

  @Column(name = "start_at", nullable = false)
  public Instant startAt;

  @Column(name = "end_at", nullable = false)
  public Instant endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "client_payment_method", length = 24)
  public ClientPaymentMethod clientPaymentMethod;

  @Column(name = "payment_proof_path", columnDefinition = "text")
  public String paymentProofPath;
  @Lob

  @Column(name = "payment_proof_data", columnDefinition = "LONGBLOB")
  public byte[] paymentProofData;

  @Column(name = "payment_proof_content_type", length = 100)
  public String paymentProofContentType;

  @Column(name = "payment_reference_declared", length = 512)
  public String paymentReferenceDeclared;

  /** Shown to the customer for pay-in-store; staff enters it in admin to mark the booking paid (confirmed). */
  @Column(name = "cash_payment_code", length = 16)
  public String cashPaymentCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_state", length = 32)
  public PaymentVerificationState paymentVerificationState;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

