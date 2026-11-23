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
 * One online checkout attempt for an order or a salon booking (subscriptions keep their own ledger in
 * {@link SubscriptionOnlinePaymentEntity}). Every start of a Paystack checkout adds a row with a new
 * reference; the record's {@code online_payment_reference} keeps only the latest. Webhooks, the return
 * page and the reconciliation job settle by attempt reference, so a payment of any attempt is found, and
 * the attempt keeps what became of it.
 *
 * <p>Enum columns are VARCHAR (not native ENUM) so new values never need a schema change.
 */
@Entity
@Table(
    name = "online_payment_attempts",
    uniqueConstraints = @UniqueConstraint(name = "uk_online_payment_attempts_reference", columnNames = "reference"),
    indexes = {
      @Index(name = "idx_online_payment_attempts_due", columnList = "outcome,last_checked_at,started_at"),
      @Index(name = "idx_online_payment_attempts_record", columnList = "kind,record_id"),
      @Index(name = "idx_online_payment_attempts_tenant_outcome", columnList = "tenant_id,outcome")
    })
public class OnlinePaymentAttemptEntity {

  public enum Kind {
    ORDER,
    BOOKING
  }

  public enum Outcome {
    /** Not known to be paid yet. */
    PENDING,
    /** Paid, and the order or booking was marked paid with it. */
    PAID,
    /** The checkout window ended without a payment. */
    NOT_PAID,
    /** Paid, but it could not be applied (record cancelled, wrong amount, ...): a person must refund. */
    NEEDS_REFUND,
    /** Paid, but the record was already paid by another attempt: a person must refund one. */
    DUPLICATE
  }

  @Id
  public UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  public Kind kind;

  @Column(name = "record_id", nullable = false)
  public UUID recordId;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(nullable = false, length = 64)
  public String reference;

  @Column(name = "amount_cents", nullable = false)
  public long amountCents;

  @Column(name = "started_at", nullable = false)
  public Instant startedAt;

  @Column(name = "last_checked_at")
  public Instant lastCheckedAt;

  @Column(name = "check_count", nullable = false)
  public int checkCount;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  public Outcome outcome = Outcome.PENDING;

  @Column(name = "outcome_at")
  public Instant outcomeAt;

  /**
   * The Paystack fee the merchant bears for this payment, in cents. Written once, when the attempt becomes
   * PAID; null means unknown (Paystack sent none, or the payment predates fee capture), never zero.
   */
  @Column(name = "fee_cents")
  public Long feeCents;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (startedAt == null) startedAt = Instant.now();
    if (outcome == null) outcome = Outcome.PENDING;
  }

  public String kindName() {
    return kind == Kind.BOOKING ? "booking" : "order";
  }
}
