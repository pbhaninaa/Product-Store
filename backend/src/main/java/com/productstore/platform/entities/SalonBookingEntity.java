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
    /** Manual bank transfer + proof upload. */
    eft,
    cash_store,
    /** In-App Peach Hosted Checkout (card and Instant EFT / PAYBYBANK). */
    peach
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

  /** Selected rail within Peach Hosted Checkout; null for cash and legacy EFT rows. */
  @Enumerated(EnumType.STRING)
  @Column(name = "peach_payment_method", length = 16)
  public PeachPaymentMethod peachPaymentMethod;

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

  @Column(name = "peach_checkout_id", length = 128)
  public String peachCheckoutId;

  @Column(name = "peach_merchant_transaction_id", length = 64)
  public String peachMerchantTransactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_state", length = 32)
  public PaymentVerificationState paymentVerificationState;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  /** Team employee (employees.id) credited for payroll when booking is confirmed. */
  @Column(name = "completed_by_employee_id")
  public UUID completedByEmployeeId;

  /** When the booking became confirmed (payroll / attribution timestamp). */
  @Column(name = "completed_at")
  public Instant completedAt;
}

