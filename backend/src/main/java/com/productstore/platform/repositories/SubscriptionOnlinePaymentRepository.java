package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionOnlinePaymentRepository
    extends JpaRepository<SubscriptionOnlinePaymentEntity, UUID> {

  /**
   * Subscription payments in the given status started since {@code since}, least recently checked first
   * (never checked first), then oldest (reconciliation).
   */
  @Query(
      """
      select p from SubscriptionOnlinePaymentEntity p
      where p.status = :status and p.onlinePaymentReference is not null and p.createdAt >= :since
      order by p.lastCheckedAt asc nulls first, p.createdAt asc
      """)
  List<SubscriptionOnlinePaymentEntity> findByStatusSince(
      @Param("status") String status,
      @Param("since") java.time.Instant since,
      org.springframework.data.domain.Pageable page);

  /** Records a reconciliation check (backoff). */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.transaction.annotation.Transactional
  @Query(
      """
      update SubscriptionOnlinePaymentEntity p
      set p.lastCheckedAt = :now, p.checkCount = coalesce(p.checkCount, 0) + 1
      where p.id = :id
      """)
  int markChecked(@Param("id") UUID id, @Param("now") java.time.Instant now);

  // Payment notifications lock the row so concurrent deliveries cannot both settle it.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionOnlinePaymentEntity p where p.onlinePaymentReference = :reference")
  Optional<SubscriptionOnlinePaymentEntity> lockByOnlinePaymentReference(
      @Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionOnlinePaymentEntity p where p.id = :id")
  Optional<SubscriptionOnlinePaymentEntity> lockById(@Param("id") UUID id);
}
