package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.ProductEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
  @Query(
      """
      select p from ProductEntity p
      where p.tenantId = :tenantId
        and p.archivedAt is null
      order by p.createdAt desc
      """)
  List<ProductEntity> findActiveByTenant(@Param("tenantId") UUID tenantId);

  @Query(
      """
      select p from ProductEntity p
      where p.tenantId = :tenantId
        and p.archivedAt is null
        and p.id in :ids
      """)
  List<ProductEntity> findActiveByTenantAndIds(
      @Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

  long countByTenantId(UUID tenantId);

  @Query(
      """
      select count(p) from ProductEntity p
      where p.tenantId = :tenantId
        and p.archivedAt is null
      """)
  long countActiveByTenant(@Param("tenantId") UUID tenantId);

  long count();

  @Query(
      """
      select count(p) from ProductEntity p where p.archivedAt is null
      """)
  long countActiveAll();

  List<ProductEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}

