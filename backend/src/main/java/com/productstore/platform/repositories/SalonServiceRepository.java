package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonServiceEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonServiceRepository extends JpaRepository<SalonServiceEntity, UUID> {
  @Query(
      """
      select s from SalonServiceEntity s
      where s.tenantId = :tenantId
        and s.active = true
      order by s.createdAt desc
      """)
  List<SalonServiceEntity> findActiveByTenant(@Param("tenantId") UUID tenantId);

  Optional<SalonServiceEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<SalonServiceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  long countByTenantIdAndActiveTrue(UUID tenantId);

  @Query("select count(s) from SalonServiceEntity s where s.active = true")
  long countAllActive();

  long count();
}

