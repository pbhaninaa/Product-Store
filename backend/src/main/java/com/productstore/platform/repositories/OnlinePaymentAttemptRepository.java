package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OnlinePaymentAttemptRepository extends JpaRepository<OnlinePaymentAttemptEntity, UUID> {

  Optional<OnlinePaymentAttemptEntity> findByReference(String reference);

  /**
   * Settling locks the attempt first (then its record), so concurrent deliveries take turns. Locked by
   * primary key after a plain lookup, so a reference that does not exist never takes a MySQL gap lock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from OnlinePaymentAttemptEntity a where a.id = :id")
  Optional<OnlinePaymentAttemptEntity> lockById(@Param("id") UUID id);

  List<OnlinePaymentAttemptEntity> findByKindAndRecordIdAndOutcome(
      OnlinePaymentAttemptEntity.Kind kind, UUID recordId, OnlinePaymentAttemptEntity.Outcome outcome);

  Optional<OnlinePaymentAttemptEntity> findByKindAndRecordIdAndReference(
      OnlinePaymentAttemptEntity.Kind kind, UUID recordId, String reference);

  /**
   * Still pending attempts started since {@code since}, least recently checked first (never checked first),
   * then oldest. Served by {@code idx_online_payment_attempts_due}.
   */
  @Query(
      """
      select a from OnlinePaymentAttemptEntity a
      where a.outcome = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Outcome.PENDING
        and a.startedAt >= :since
      order by a.lastCheckedAt asc nulls first, a.startedAt asc
      """)
  List<OnlinePaymentAttemptEntity> findPendingSince(@Param("since") Instant since, Pageable page);

  @Modifying
  @Transactional
  @Query(
      """
      update OnlinePaymentAttemptEntity a
      set a.lastCheckedAt = :now, a.checkCount = a.checkCount + 1
      where a.id = :id
      """)
  int markChecked(@Param("id") UUID id, @Param("now") Instant now);

  /**
   * Payable payments of every merchant (provider payouts): PAID order and booking attempts that are in no
   * payout and whose order or booking (of the same tenant) is not cancelled. NEEDS_REFUND, DUPLICATE,
   * PENDING and NOT_PAID are excluded by the outcome; subscriptions never have attempts. Ordered by tenant,
   * then settle time.
   */
  @Query(
      """
      select a from OnlinePaymentAttemptEntity a
      where a.outcome = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Outcome.PAID
        and not exists (
          select 1 from ProviderPayoutLineEntity l where l.paymentAttemptId = a.id)
        and (
          (a.kind = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Kind.ORDER
            and exists (
              select 1 from OrderEntity o
              where o.id = a.recordId and o.tenantId = a.tenantId
                and o.status <> com.productstore.platform.entities.OrderEntity$OrderStatus.cancelled))
          or (a.kind = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Kind.BOOKING
            and exists (
              select 1 from SalonBookingEntity b
              where b.id = a.recordId and b.tenantId = a.tenantId
                and b.status <> com.productstore.platform.entities.SalonBookingEntity$Status.cancelled)))
      order by a.tenantId asc, a.outcomeAt asc, a.id asc
      """)
  List<OnlinePaymentAttemptEntity> findPayable();

  /** {@link #findPayable()} for one merchant. */
  @Query(
      """
      select a from OnlinePaymentAttemptEntity a
      where a.tenantId = :tenantId
        and a.outcome = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Outcome.PAID
        and not exists (
          select 1 from ProviderPayoutLineEntity l where l.paymentAttemptId = a.id)
        and (
          (a.kind = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Kind.ORDER
            and exists (
              select 1 from OrderEntity o
              where o.id = a.recordId and o.tenantId = a.tenantId
                and o.status <> com.productstore.platform.entities.OrderEntity$OrderStatus.cancelled))
          or (a.kind = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Kind.BOOKING
            and exists (
              select 1 from SalonBookingEntity b
              where b.id = a.recordId and b.tenantId = a.tenantId
                and b.status <> com.productstore.platform.entities.SalonBookingEntity$Status.cancelled)))
      order by a.outcomeAt asc, a.id asc
      """)
  List<OnlinePaymentAttemptEntity> findPayableForTenant(@Param("tenantId") UUID tenantId);

  /** Pending attempts whose checkout window has passed become NOT_PAID. */
  @Modifying
  @Transactional
  @Query(
      """
      update OnlinePaymentAttemptEntity a
      set a.outcome = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Outcome.NOT_PAID,
          a.outcomeAt = :now
      where a.outcome = com.productstore.platform.entities.OnlinePaymentAttemptEntity$Outcome.PENDING
        and a.startedAt < :before
      """)
  int closeExpired(@Param("before") Instant before, @Param("now") Instant now);
}
