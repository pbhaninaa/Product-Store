package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentRefundEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlinePaymentRefundRepository extends JpaRepository<OnlinePaymentRefundEntity, UUID> {

  Optional<OnlinePaymentRefundEntity> findByPaymentAttemptId(UUID paymentAttemptId);

  List<OnlinePaymentRefundEntity> findByRecordIdOrderByRequestedAtAsc(UUID recordId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from OnlinePaymentRefundEntity r where r.id = :id")
  Optional<OnlinePaymentRefundEntity> lockById(@Param("id") UUID id);

  /** Refunds whose next automatic try is due, oldest due first. */
  @Query(
      """
      select r.id from OnlinePaymentRefundEntity r
      where r.status = com.productstore.platform.entities.OnlinePaymentRefundEntity$Status.PENDING
        and r.nextTryAt is not null
        and r.nextTryAt <= :now
      order by r.nextTryAt asc
      """)
  List<UUID> findDueIds(@Param("now") Instant now, Pageable page);
}
