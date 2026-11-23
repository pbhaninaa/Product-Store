package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
    name = "salon_bookings",
    indexes = {
      @Index(name = "idx_bookings_online_merchant_tx", columnList = "online_payment_reference"),
      @Index(name = "idx_bookings_online_checkout", columnList = "online_payment_provider_id")
    })
public class SalonBookingEntity {
  public enum Status {
    pending,
    confirmed,
    in_progress,
    completed,
    cancelled
  }

  /** Mirrors {@link com.productstore.platform.entities.OrderEntity.PaymentMethod} naming for the storefront. */
  public enum ClientPaymentMethod {
    /** Manual bank transfer + proof upload. */
    eft,
    cash_store,
    /** Online hosted checkout (card or Instant EFT) through the platform payment provider. */
    online
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

  /** Selected online payment rail; null for cash and manual EFT rows. */
  @Enumerated(EnumType.STRING)
  @Column(name = "online_payment_channel", length = 16)
  public OnlinePaymentChannel onlinePaymentChannel;

  /** Logged-in CLIENT account that placed this booking, when known. */
  @Column(name = "client_user_id")
  public UUID clientUserId;

  /** Promo discount applied at booking. */
  @Column(name = "discount_zar", precision = 12, scale = 2)
  public java.math.BigDecimal discountZar;

  /** What the customer owes (service price less discount), fixed when booked so later price edits don't change it. */
  @Column(name = "amount_zar", precision = 12, scale = 2)
  public java.math.BigDecimal amountZar;

  /** Promotion whose use this booking holds; released if the booking is cancelled. */
  @Column(name = "promotion_id")
  public UUID promotionId;

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

  /** The payment provider's transaction id once an online payment is confirmed. */
  @Column(name = "online_payment_provider_id", length = 128)
  public String onlinePaymentProviderId;

  /** Our reference for the latest online checkout attempt; matches provider notifications. */
  @Column(name = "online_payment_reference", length = 64)
  public String onlinePaymentReference;

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

