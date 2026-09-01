package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "order_reviews",
    uniqueConstraints = @UniqueConstraint(name = "uk_order_reviews_order", columnNames = "order_id"))
public class OrderReviewEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "order_id", nullable = false)
  public UUID orderId;

  @Column(name = "customer_email", nullable = false, length = 320)
  public String customerEmail;

  @Column(nullable = false)
  public int rating;

  @Column(length = 2000)
  public String comment;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
