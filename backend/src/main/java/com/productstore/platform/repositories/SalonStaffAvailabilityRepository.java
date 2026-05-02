package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonStaffAvailabilityEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonStaffAvailabilityRepository
    extends JpaRepository<SalonStaffAvailabilityEntity, UUID> {

  Optional<SalonStaffAvailabilityEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<SalonStaffAvailabilityEntity> findByTenantIdOrderByStaffIdAscDayOfWeekAscStartTimeAsc(UUID tenantId);

  @Query(
      """
      select a from SalonStaffAvailabilityEntity a
      where a.tenantId = :tenantId
        and a.staffId in :staffIds
        and a.dayOfWeek = :dayOfWeek
      """)
  List<SalonStaffAvailabilityEntity> findForStaffOnDay(
      @Param("tenantId") UUID tenantId,
      @Param("staffIds") List<UUID> staffIds,
      @Param("dayOfWeek") Integer dayOfWeek);
}

