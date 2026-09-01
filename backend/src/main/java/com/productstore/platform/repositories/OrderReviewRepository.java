package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OrderReviewEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReviewRepository extends JpaRepository<OrderReviewEntity, UUID> {
  Optional<OrderReviewEntity> findByOrderId(UUID orderId);

  boolean existsByOrderId(UUID orderId);

  List<OrderReviewEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  @Query("select coalesce(avg(r.rating), 0) from OrderReviewEntity r where r.tenantId = :tenantId")
  Double averageRatingByTenant(@Param("tenantId") UUID tenantId);

  long countByTenantId(UUID tenantId);
}
