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

  /** Subscription payments in the given status started since {@code since}, oldest first (reconciliation). */
  @Query(
      """
      select p from SubscriptionOnlinePaymentEntity p
      where p.status = :status and p.onlinePaymentReference is not null and p.createdAt >= :since
      order by p.createdAt asc
      """)
  List<SubscriptionOnlinePaymentEntity> findByStatusSince(
      @Param("status") String status,
      @Param("since") java.time.Instant since,
      org.springframework.data.domain.Pageable page);

  // Payment notifications lock the row so concurrent deliveries cannot both settle it.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionOnlinePaymentEntity p where p.onlinePaymentReference = :reference")
  Optional<SubscriptionOnlinePaymentEntity> lockByOnlinePaymentReference(
      @Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionOnlinePaymentEntity p where p.id = :id")
  Optional<SubscriptionOnlinePaymentEntity> lockById(@Param("id") UUID id);
}
