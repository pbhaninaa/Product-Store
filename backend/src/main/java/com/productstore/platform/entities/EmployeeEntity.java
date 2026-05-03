package com.productstore.platform.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class EmployeeEntity {

    @Id
    public UUID id;

    /** Merchant owner tenant id */
    @Column(nullable = false, name = "tenant_id")
    public UUID tenantId;

    /** Employee user profile id */
    @Column(nullable = false, name = "user_profile_id")
    public UUID userProfileId;

    @Column(nullable = false, length = 50)
    public String role;

    @Column(name = "pay_method", length = 20)
    public String payMethod;

    @Column(name = "pay_rate")
    public Double payRate;

    /**
     * Merchant-defined target period for this employee (e.g. DAILY/WEEKLY/MONTHLY).
     */
    @Column(name = "target_period", length = 20)
    public String targetPeriod;

    /** Merchant-defined target value for this employee (units decided by merchant). */
    @Column(name = "target_value")
    public Double targetValue;

    /** Bonus percentage for this employee (0-100). */
    @Column(name = "bonus_percentage")
    public Double bonusPercentage;

    @Column(nullable = false, name = "is_active")
    public boolean isActive = true;

    @Column(name = "hired_at")
    public Instant hiredAt;

    @Column(name = "created_by", length = 120)
    public String createdBy;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (hiredAt == null) hiredAt = Instant.now();
    }
}
