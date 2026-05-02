package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import com.productstore.platform.services.auth.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memberships")
public class MembershipEntity {
  @Id
  public UUID id;

  @Column(name = "user_id", nullable = false)
  public UUID userId;

  @Column(name = "tenant_id")
  public UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Role role;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

