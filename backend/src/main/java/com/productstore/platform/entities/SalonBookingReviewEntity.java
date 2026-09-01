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
    name = "salon_booking_reviews",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_salon_booking_reviews_booking", columnNames = "booking_id"))
public class SalonBookingReviewEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "booking_id", nullable = false)
  public UUID bookingId;

  @Column(name = "customer_email", nullable = false, length = 320)
  public String customerEmail;

  @Column(nullable = false)
  public int rating;

  @Column(length = 2000)
  public String comment;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
