package com.productstore.platform.entities;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salon_staff_availability")
public class SalonStaffAvailabilityEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "staff_id", nullable = false)
  public UUID staffId;

  @Column(name = "day_of_week", nullable = false)
  public Integer dayOfWeek; // 1(Mon) .. 7(Sun) to match java.time.DayOfWeek

  @Column(name = "start_time", nullable = false)
  public LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  public LocalTime endTime;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

