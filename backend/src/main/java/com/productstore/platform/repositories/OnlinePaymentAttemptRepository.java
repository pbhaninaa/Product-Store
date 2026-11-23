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
