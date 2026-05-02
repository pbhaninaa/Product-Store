package com.productstore.platform.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
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
}

