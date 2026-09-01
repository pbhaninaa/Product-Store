package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingReviewEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonBookingReviewRepository extends JpaRepository<SalonBookingReviewEntity, UUID> {
  Optional<SalonBookingReviewEntity> findByBookingId(UUID bookingId);

  boolean existsByBookingId(UUID bookingId);

  @Query(
      "select coalesce(avg(r.rating), 0) from SalonBookingReviewEntity r where r.tenantId = :tenantId")
  Double averageRatingByTenant(@Param("tenantId") UUID tenantId);

  long countByTenantId(UUID tenantId);
}
