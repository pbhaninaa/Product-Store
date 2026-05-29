package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class TenantEntity {
  public enum SubscriptionPlan {
    STARTER,
    STANDARD,
    PREMIUM
  }

  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public String slug;

  @Column(nullable = false)
  public String name;

  @Column(name = "modules_json", nullable = false, columnDefinition = "json")
  public String modulesJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_plan", length = 32)
  public SubscriptionPlan subscriptionPlan;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

