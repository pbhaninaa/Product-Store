package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonBookingRepository extends JpaRepository<SalonBookingEntity, UUID> {

  List<SalonBookingEntity> findByTenantIdOrderByStartAtDesc(UUID tenantId);
  @Query(
      """
      select b from SalonBookingEntity b
      where b.tenantId = :tenantId
        and b.status in :statuses
        and b.startAt < :endAt
        and b.endAt > :startAt
      """)
  List<SalonBookingEntity> findOverlapping(
      @Param("tenantId") UUID tenantId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt,
      @Param("statuses") List<SalonBookingEntity.Status> statuses);

  long count();

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndStatus(UUID tenantId, SalonBookingEntity.Status status);

  long countByStatus(SalonBookingEntity.Status status);

  @Query(
      """
      select b from SalonBookingEntity b
      where b.tenantId = :tenantId
        and b.status in (
          com.productstore.platform.entities.SalonBookingEntity$Status.confirmed,
          com.productstore.platform.entities.SalonBookingEntity$Status.in_progress,
          com.productstore.platform.entities.SalonBookingEntity$Status.completed)
        and b.completedByEmployeeId = :employeeId
      order by b.startAt desc
      """)
  List<SalonBookingEntity> findConfirmedByTenantAndEmployee(
      @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

  @Query(
      """
      select b from SalonBookingEntity b
      where b.tenantId = :tenantId
        and b.id = :bookingId
      """)
  SalonBookingEntity findOneByTenantAndId(
      @Param("tenantId") UUID tenantId, @Param("bookingId") UUID bookingId);

  List<SalonBookingEntity> findTop50ByOrderByStartAtDesc();

  SalonBookingEntity findFirstByPeachMerchantTransactionId(String peachMerchantTransactionId);

  SalonBookingEntity findFirstByPeachCheckoutId(String peachCheckoutId);

  List<SalonBookingEntity> findByClientUserIdOrderByStartAtDesc(UUID clientUserId);
}

