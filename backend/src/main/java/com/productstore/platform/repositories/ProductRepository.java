package com.productstore.platform.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.ProductEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  List<ProductEntity> findByTenantIdInAndArchivedAtIsNull(Collection<UUID> tenantIds);

  List<ProductEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  /** Locks the cart's products (in id order, so concurrent checkouts cannot deadlock) while stock is reserved. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select p from ProductEntity p
      where p.tenantId = :tenantId
        and p.archivedAt is null
        and p.id in :ids
      order by p.id
      """)
  List<ProductEntity> lockActiveByTenantAndIds(
      @Param("tenantId") UUID tenantId, @Param("ids") List<UUID> ids);

  /** Takes stock in one statement; returns 0 (and changes nothing) when there is not enough. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update ProductEntity p set p.stock = p.stock - :qty
      where p.id = :id and p.tenantId = :tenantId and p.stock >= :qty
      """)
  int deductStock(@Param("tenantId") UUID tenantId, @Param("id") UUID id, @Param("qty") int qty);
}

