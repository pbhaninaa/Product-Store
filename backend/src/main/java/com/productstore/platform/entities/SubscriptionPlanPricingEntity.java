package com.productstore.platform.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "subscription_plan_pricing",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tier"}))
public class SubscriptionPlanPricingEntity {
  @Id
  public UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public TenantEntity.SubscriptionPlan tier;

  @Column(nullable = false, name = "subscription_fee")
  public double subscriptionFee;

  @Column(nullable = false, name = "billing_period_days")
  public int billingPeriodDays = 30;

  @Column(nullable = false, name = "feature_insights")
  public boolean featureInsights;

  @Column(nullable = false, name = "feature_email_alerts")
  public boolean featureEmailAlerts;

  @Column(nullable = false, name = "feature_whatsapp")
  public boolean featureWhatsapp;

  @Column(nullable = false, name = "feature_payroll")
  public boolean featurePayroll;

  /** Max login team seats; -1 = unlimited */
  @Column(nullable = false, name = "max_employees")
  public int maxEmployees = -1;

  /** Max products; -1 = unlimited */
  @Column(nullable = false, name = "max_products")
  public int maxProducts = -1;

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }
}
