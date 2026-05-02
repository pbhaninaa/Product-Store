package com.productstore.platform.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salon_services")
public class SalonServiceEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "duration_minutes", nullable = false)
  public Integer durationMinutes;

  @Column(name = "price_zar", nullable = false)
  public BigDecimal priceZar;

  @Column(name = "image_url", nullable = false, length = 2000)
  public String imageUrl = "";

  @Column(name = "image_path", nullable = false, length = 1024)
  public String imagePath = "";

  @Column(nullable = false)
  public Boolean active;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

