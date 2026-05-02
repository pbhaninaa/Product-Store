package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salon_staff")
public class SalonStaffEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "display_name", nullable = false)
  public String displayName;

  @Column(nullable = false)
  public Boolean active;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

