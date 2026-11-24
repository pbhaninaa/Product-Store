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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Audit trail of a booking fee (NFR-3): every payment, refund step and kept fee, with time, amount and reason. */
@Entity
@Table(
    name = "booking_fee_events",
    indexes = @Index(name = "idx_booking_fee_events_booking", columnList = "booking_id,created_at"))
public class BookingFeeEventEntity {

  public enum Type {
    FEE_PAID,
    FEE_KEPT,
    REFUND_REQUESTED,
    REFUND_SUBMITTED,
    REFUND_FAILED,
    REFUND_FLAGGED,
    REFUND_PROCESSED
  }

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "booking_id", nullable = false)
  public UUID bookingId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 24)
  public Type type;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(length = 32)
  public SalonBookingEntity.BookingFeeReason reason;

  @Column(name = "amount_cents", nullable = false)
  public long amountCents;

  @Column(name = "payment_reference", length = 64)
  public String paymentReference;

  /** Short, non-personal detail (e.g. a Paystack error or the payout a fee was already released in). */
  @Column(length = 255)
  public String detail;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
