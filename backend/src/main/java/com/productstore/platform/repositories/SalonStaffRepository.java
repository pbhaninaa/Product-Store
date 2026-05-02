package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonStaffEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonStaffRepository extends JpaRepository<SalonStaffEntity, UUID> {
  @Query(
      """
      select s from SalonStaffEntity s
      where s.tenantId = :tenantId
        and s.active = true
      order by s.createdAt desc
      """)
  List<SalonStaffEntity> findActiveByTenant(@Param("tenantId") UUID tenantId);

  Optional<SalonStaffEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<SalonStaffEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  long countByTenantIdAndActiveTrue(UUID tenantId);

  @Query("select count(s) from SalonStaffEntity s where s.active = true")
  long countAllActive();

  long count();
}

