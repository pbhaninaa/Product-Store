package com.productstore.platform.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "promotions",
    indexes = {@Index(name = "idx_promotions_tenant", columnList = "tenant_id")})
public class PromotionEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(length = 2000)
  public String description;

  /** PERCENTAGE or FIXED */
  @Column(nullable = false, length = 20, name = "discount_type")
  public String discountType;

  @Column(nullable = false, name = "discount_value")
  public Double discountValue;

  @Column(name = "minimum_order_value")
  public Double minimumOrderValue;

  @Column(nullable = false, name = "start_date")
  public LocalDate startDate;

  @Column(nullable = false, name = "end_date")
  public LocalDate endDate;

  @Column(nullable = false, name = "is_active")
  public boolean active = true;

  /** Comma-separated category names; empty = all products */
  @Column(name = "applicable_categories", length = 2000)
  public String applicableCategories;

  @Column(name = "usage_limit")
  public Integer usageLimit;

  @Column(name = "usage_count")
  public Integer usageCount = 0;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (usageCount == null) usageCount = 0;
    if (createdAt == null) createdAt = Instant.now();
  }
}
