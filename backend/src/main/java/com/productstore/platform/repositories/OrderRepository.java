package com.productstore.platform.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Online orders whose payment may still arrive: awaiting payment, or cancelled by expiry (a late payment
   * revives them). Oldest first, for payment reconciliation.
   */
  @Query(
      """
      select o from OrderEntity o
      where o.paymentMethod = com.productstore.platform.entities.OrderEntity$PaymentMethod.online
        and o.onlinePaymentReference is not null
        and o.createdAt >= :since
        and (o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.pending_payment
             or (o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.cancelled
                 and o.expiredAt is not null))
      order by o.createdAt asc
      """)
  List<OrderEntity> findOnlineAwaitingConfirmation(
      @Param("since") java.time.Instant since, org.springframework.data.domain.Pageable page);

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
}
