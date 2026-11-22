package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalonBookingRepository extends JpaRepository<SalonBookingEntity, UUID> {

  List<SalonBookingEntity> findByTenantIdOrderByStartAtDesc(UUID tenantId);

  /** Track page lookup: only this customer's bookings, not every booking in the shop. */
  List<SalonBookingEntity> findByTenantIdAndCustomerEmailIgnoreCase(UUID tenantId, String customerEmail);
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

  /**
   * Staff "My income" upcoming work only: bookings credited to the employee in one of {@code statuses}.
   * Never used by owner payroll (see {@code EmployeeService#myExpectedIncome}).
   */
  List<SalonBookingEntity> findByTenantIdAndCompletedByEmployeeIdAndStatusIn(
      UUID tenantId, UUID completedByEmployeeId, Collection<SalonBookingEntity.Status> statuses);

  /**
   * Staff "My income" upcoming work only: bookings made with a salon staff member that nobody has been
   * credited for yet. Never used by owner payroll.
   */
  List<SalonBookingEntity> findByTenantIdAndStaffIdAndCompletedByEmployeeIdIsNullAndStatusIn(
      UUID tenantId, UUID staffId, Collection<SalonBookingEntity.Status> statuses);

  @Query(
      """
      select b from SalonBookingEntity b
      where b.tenantId = :tenantId
        and b.id = :bookingId
      """)
  SalonBookingEntity findOneByTenantAndId(
      @Param("tenantId") UUID tenantId, @Param("bookingId") UUID bookingId);

  List<SalonBookingEntity> findTop50ByOrderByStartAtDesc();

  List<SalonBookingEntity> findByClientUserIdOrderByStartAtDesc(UUID clientUserId);

  // Payment notifications lock the row so concurrent ITNs/webhooks cannot both settle it.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from SalonBookingEntity b where b.peachMerchantTransactionId = :reference")
  List<SalonBookingEntity> lockByPeachMerchantTransactionId(@Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from SalonBookingEntity b where b.peachCheckoutId = :checkoutId")
  List<SalonBookingEntity> lockByPeachCheckoutId(@Param("checkoutId") String checkoutId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from SalonBookingEntity b where b.id = :id")
  Optional<SalonBookingEntity> lockById(@Param("id") UUID id);
}
