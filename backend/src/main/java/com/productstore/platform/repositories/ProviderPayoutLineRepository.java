package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.ProviderPayoutLineEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderPayoutLineRepository extends JpaRepository<ProviderPayoutLineEntity, UUID> {

  List<ProviderPayoutLineEntity> findByPayoutIdOrderByPaidAtAscIdAsc(UUID payoutId);

  /** Which of these payments are already in a payout. */
  @Query("select l.paymentAttemptId from ProviderPayoutLineEntity l where l.paymentAttemptId in :attemptIds")
  List<UUID> findReleasedAttemptIds(@Param("attemptIds") Collection<UUID> attemptIds);

  /** A store's paid-out payments settled in {@code [from, to)}. */
  List<ProviderPayoutLineEntity> findByTenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
      UUID tenantId, Instant from, Instant to);

  List<ProviderPayoutLineEntity> findByTenantId(UUID tenantId);
}
