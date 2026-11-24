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

    /** Linked login user id ({@link UserEntity#id}) */
    @Column(nullable = false, name = "user_profile_id")
    public UUID userProfileId;

    @Column(name = "display_name", length = 120)
    public String displayName;

    @Column(nullable = false, length = 50)
    public String role;

    @Column(name = "pay_method", length = 20)
    public String payMethod;

    @Column(name = "pay_rate")
    public Double payRate;

    /**
     * Order pay: {@link com.productstore.platform.constants.EmployeeOrderPayMethod} name. NULL (every row
     * created before this column existed) means NONE, so orders earn nothing until the owner sets it.
     */
    @Column(name = "order_pay_method", length = 20)
    public String orderPayMethod;

    /** Rand amount per order (FIXED_PER_ORDER) or percentage 0 to 100 (PERCENT_OF_ORDER). */
    @Column(name = "order_pay_rate")
    public Double orderPayRate;

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

    /**
     * Comma-separated {@link com.productstore.platform.constants.MerchantPermission} names chosen by
     * the owner. NULL (every row created before this column existed, and new staff until the owner
     * edits them) means the legacy staff default; an empty string means no optional access.
     */
    @Column(name = "merchant_permissions", length = 500)
    public String merchantPermissions;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (hiredAt == null) hiredAt = Instant.now();
    }
}
