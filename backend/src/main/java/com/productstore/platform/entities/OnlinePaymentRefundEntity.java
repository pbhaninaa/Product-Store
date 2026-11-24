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
 * A refund the app owes for one paid online payment attempt (today: booking fees). At most one per attempt
 * (unique key), so a payment can never be refunded twice by the app. Paystack is called after the row is
 * committed; failures are retried with a backoff and flagged for support.
 */
@Entity
@Table(
    name = "online_payment_refunds",
    uniqueConstraints = @UniqueConstraint(name = "uk_online_payment_refunds_attempt", columnNames = "payment_attempt_id"),
    indexes = {
      @Index(name = "idx_online_payment_refunds_due", columnList = "status,next_try_at"),
      @Index(name = "idx_online_payment_refunds_record", columnList = "record_id")
    })
public class OnlinePaymentRefundEntity {

  public enum Status {
    /** To be sent to Paystack (first try or a retry after a failure). */
    PENDING,
    /** Paystack accepted the refund (queued or processing). */
    SUBMITTED,
    /** Paystack reported the refund processed. */
    PROCESSED,
    /** Not sent automatically (the payment was already paid out to the provider): a person refunds. */
    MANUAL
  }

  @Id
  public UUID id;

  @Column(name = "payment_attempt_id", nullable = false)
  public UUID paymentAttemptId;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "record_id", nullable = false)
  public UUID recordId;

  @Column(name = "payment_reference", nullable = false, length = 64)
  public String paymentReference;

  @Column(name = "amount_cents", nullable = false)
  public long amountCents;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  public SalonBookingEntity.BookingFeeReason reason;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  public Status status = Status.PENDING;

  /** Times the refund was sent to Paystack (or claimed for sending). */
  @Column(nullable = false)
  public int tries;

  /** When the next automatic try is due; null when no try is due. */
  @Column(name = "next_try_at")
  public Instant nextTryAt;

  /** Last Paystack error (never secrets). */
  @Column(name = "last_error", length = 255)
  public String lastError;

  /** True once a person should look at it (a failure, or not refundable automatically). */
  @Column(name = "flagged_for_support", nullable = false)
  public boolean flaggedForSupport;

  /** Paystack's refund id once accepted. */
  @Column(name = "provider_refund_id", length = 64)
  public String providerRefundId;

  @Column(name = "requested_at", nullable = false)
  public Instant requestedAt;

  @Column(name = "submitted_at")
  public Instant submittedAt;

  @Column(name = "processed_at")
  public Instant processedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (requestedAt == null) requestedAt = Instant.now();
    if (status == null) status = Status.PENDING;
  }
}
