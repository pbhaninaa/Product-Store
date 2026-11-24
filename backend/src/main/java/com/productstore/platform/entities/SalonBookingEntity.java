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

  /**
   * Handover code shown to the customer for every payment method. Staff enter it to start the service; for
   * pay-in-store it also marks the booking paid. Column keeps its original name.
   */
  @Column(name = "cash_payment_code", length = 16)
  public String handoverCode;

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

  /** What became of the booking fee of a pay-in-store booking (see {@code BookingFeeService}). */
  public enum BookingFeeStatus {
    /** The fee checkout is open; the booking is cancelled if it is not paid in time. */
    awaiting_payment,
    /** Paid online; the remainder is due at the shop. */
    paid,
    /** A refund is owed and on its way (submitting, retrying, or waiting for a person). */
    refund_pending,
    /** Paystack accepted the refund. */
    refunded,
    /** Kept by the provider (late cancellation or no-show). */
    kept,
    /** The booking was cancelled or expired before the fee was paid. */
    not_paid
  }

  /** Why a fee was refunded or kept (audit, receipts). */
  public enum BookingFeeReason {
    customer_cancellation,
    late_cancellation,
    no_show,
    provider_cancellation,
    payment_after_cancellation
  }

  /** Booking fee percent fixed when booked; null when no fee applies. */
  @Column(name = "booking_fee_percent")
  public Integer bookingFeePercent;

  /** Booking fee (part of {@link #amountZar}, not added to it) fixed when booked; null when no fee applies. */
  @Column(name = "booking_fee_zar", precision = 12, scale = 2)
  public java.math.BigDecimal bookingFeeZar;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(name = "booking_fee_status", length = 20)
  public BookingFeeStatus bookingFeeStatus;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(name = "booking_fee_reason", length = 32)
  public BookingFeeReason bookingFeeReason;

  @Column(name = "booking_fee_paid_at")
  public Instant bookingFeePaidAt;

  /** When the fee was refunded, kept or closed unpaid. */
  @Column(name = "booking_fee_outcome_at")
  public Instant bookingFeeOutcomeAt;

  /** When the booking was cancelled (by anyone, expiry or no-show); null for older rows. */
  @Column(name = "cancelled_at")
  public Instant cancelledAt;

  /** When the provider marked the customer as a no-show (the booking is then cancelled). */
  @Column(name = "no_show_at")
  public Instant noShowAt;

  /** True when this booking carries a booking fee (pay-in-store with a fee fixed at booking). */
  public boolean hasBookingFee() {
    return clientPaymentMethod == ClientPaymentMethod.cash_store
        && bookingFeeZar != null
        && bookingFeeZar.signum() > 0
        && bookingFeeStatus != null;
  }
}

