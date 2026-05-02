package com.productstore.platform.repositories;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderItemEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {
  @Query(
      """
      select oi from OrderItemEntity oi
      where oi.tenantId = :tenantId
        and oi.orderId = :orderId
      """)
  List<OrderItemEntity> findAllByTenantAndOrderId(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

  @Query(
      """
      select coalesce(sum(oi.quantity), 0)
      from OrderItemEntity oi
      join OrderEntity o on o.id = oi.orderId
      where oi.tenantId = :tenantId
        and oi.productId = :productId
        and o.status = com.productstore.platform.entities.OrderEntity$OrderStatus.pending_payment
        and o.cancelledAt is null
      """)
  long sumPendingQtyForProduct(@Param("tenantId") UUID tenantId, @Param("productId") UUID productId);

  @Modifying
  void deleteByTenantIdAndOrderId(UUID tenantId, UUID orderId);
}

