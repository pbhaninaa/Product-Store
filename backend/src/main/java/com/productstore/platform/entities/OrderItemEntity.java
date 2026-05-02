package com.productstore.platform.entities;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "order_id", nullable = false)
  public UUID orderId;

  @Column(name = "product_id", nullable = false)
  public UUID productId;

  @Column(nullable = false)
  public Integer quantity;

  @Column(name = "unit_price_zar", nullable = false)
  public BigDecimal unitPriceZar;

  @Column(name = "line_total_zar", nullable = false)
  public BigDecimal lineTotalZar;
}

