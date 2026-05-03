package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class ProductEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false)
  public String category;

  @Column(name = "price_zar", nullable = false)
  public java.math.BigDecimal priceZar;

  @Column(name = "image_url", nullable = false)
  public String imageUrl;

  @Column(name = "image_path", nullable = false)
  public String imagePath;
  @Lob

  @Column(name = "image_data", columnDefinition = "LONGBLOB")
  public byte[] imageData;

  @Column(name = "image_content_type", length = 100)
  public String imageContentType;

  @Column(nullable = false)
  public Integer stock;

  @Column(name = "archived_at")
  public Instant archivedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

