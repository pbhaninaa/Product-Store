package com.productstore.platform.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.PromotionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {
  List<PromotionEntity> findByTenantIdOrderByStartDateDesc(UUID tenantId);

  List<PromotionEntity>
      findByTenantIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
          UUID tenantId, LocalDate startDate, LocalDate endDate);

  Optional<PromotionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
