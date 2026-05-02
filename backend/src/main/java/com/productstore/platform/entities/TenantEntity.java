package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class TenantEntity {
  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public String slug;

  @Column(nullable = false)
  public String name;

  @Column(name = "modules_json", nullable = false, columnDefinition = "json")
  public String modulesJson;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

