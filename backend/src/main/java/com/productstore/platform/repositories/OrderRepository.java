package com.productstore.platform.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
  /** Track page lookup: only this customer's orders, not every order in the shop. */
  List<OrderEntity> findByTenantIdAndCustomerEmailIgnoreCase(UUID tenantId, String customerEmail);

  @Query(
      """
      select o from OrderEntity o
      where o.tenantId = :tenantId
      order by o.createdAt desc
      """)
  List<OrderEntity> findAllByTenant(@Param("tenantId") UUID tenantId);

  @Query(
      """
      select o from OrderEntity o
      where o.tenantId = :tenantId
        and o.id = :orderId
      """)
  OrderEntity findOneByTenantAndId(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndStatus(UUID tenantId, OrderEntity.OrderStatus status);

  @Query(
      """
      select coalesce(sum(o.totalZar), 0) from OrderEntity o
      where o.tenantId = :tenantId
        and o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.paid
      """)
  BigDecimal sumPaidTotalZarByTenant(@Param("tenantId") UUID tenantId);

  long count();

  long countByStatus(OrderEntity.OrderStatus status);

  @Query(
      """
      select coalesce(sum(o.totalZar), 0) from OrderEntity o
      where o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.paid
      """)
  BigDecimal sumPaidTotalZarAll();

  @Query(
      """
      select o from OrderEntity o
      where o.tenantId = :tenantId
        and o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.paid
        and o.completedByEmployeeId = :employeeId
      order by o.paymentConfirmedAt desc
      """)
  List<OrderEntity> findPaidByTenantAndEmployee(
      @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

  List<OrderEntity> findTop50ByOrderByCreatedAtDesc();

  List<OrderEntity> findByClientUserIdOrderByCreatedAtDesc(UUID clientUserId);

  // Payment notifications lock the row so concurrent webhook deliveries cannot both settle it.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrderEntity o where o.onlinePaymentReference = :reference")
  List<OrderEntity> lockByOnlinePaymentReference(@Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrderEntity o where o.id = :id")
  Optional<OrderEntity> lockById(@Param("id") UUID id);

  @Query(
      """
      select o from OrderEntity o
      where o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.pending_payment
        and o.cancelledAt is null
        and o.createdAt < :before
      """)
  List<OrderEntity> findUnpaidCreatedBefore(@Param("before") java.time.Instant before);

  /** Orders not yet handed over (or cancelled) that have no handover code (placed before every order had one). */
  @Query(
      """
      select o.id from OrderEntity o
      where (o.handoverCode is null or trim(o.handoverCode) = '')
        and o.cancelledAt is null
        and (o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.pending_payment
          or (o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.paid
            and (o.fulfillmentStatus is null
              or o.fulfillmentStatus <> com.productstore.platform.entities.OrderEntity$FulfillmentStatus.completed)))
      """)
  List<UUID> findIdsAwaitingHandoverWithoutCode();

  /** Sets the handover code unless the order already has one (safe to run on several instances at once). */
  @Modifying
  @Transactional
  @Query(
      """
      update OrderEntity o set o.handoverCode = :code
      where o.id = :id and (o.handoverCode is null or trim(o.handoverCode) = '')
      """)
  int assignHandoverCodeIfMissing(@Param("id") UUID id, @Param("code") String code);
}
