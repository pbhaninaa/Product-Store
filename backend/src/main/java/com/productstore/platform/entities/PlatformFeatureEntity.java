package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "platform_features",
    uniqueConstraints = @UniqueConstraint(columnNames = {"feature_key"}))
public class PlatformFeatureEntity {
  @Id
  public UUID id;

  @Column(name = "feature_key", nullable = false, length = 64)
  public String featureKey;

  @Column(nullable = false)
  public boolean enabled = true;

  @Column(length = 240)
  public String description;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (updatedAt == null) updatedAt = Instant.now();
  }
}
