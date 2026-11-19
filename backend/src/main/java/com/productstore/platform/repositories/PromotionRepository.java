package com.productstore.platform.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.PromotionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {
  List<PromotionEntity> findByTenantIdOrderByStartDateDesc(UUID tenantId);

  List<PromotionEntity>
      findByTenantIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          UUID tenantId, LocalDate startDate, LocalDate endDate);

  Optional<PromotionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Claims one use in a single statement, so concurrent orders cannot go past the limit. 0 = exhausted. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update PromotionEntity p set p.usageCount = coalesce(p.usageCount, 0) + 1
      where p.id = :id and (p.usageLimit is null or coalesce(p.usageCount, 0) < p.usageLimit)
      """)
  int claimUse(@Param("id") UUID id);

  /** Counts a use even past the limit (a customer already paid with this discount). */
  @Modifying(flushAutomatically = true)
  @Query("update PromotionEntity p set p.usageCount = coalesce(p.usageCount, 0) + 1 where p.id = :id")
  int forceUse(@Param("id") UUID id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update PromotionEntity p set p.usageCount = p.usageCount - 1 where p.id = :id and p.usageCount > 0")
  int releaseUse(@Param("id") UUID id);
}
