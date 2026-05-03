package com.productstore.platform.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_payroll_job_marks")
public class EmployeePayrollJobMarkEntity {

    @Id
    public UUID id;

    /** Merchant owner tenant id */
    @Column(nullable = false, name = "tenant_id")
    public UUID tenantId;

    @Column(nullable = false, name = "employee_record_id")
    public UUID employeeRecordId;

    @Column(nullable = false, name = "job_id")
    public UUID jobId;

    /** Job type discriminator (e.g. ORDER, SALON_BOOKING, etc.) */
    @Column(nullable = false, name = "job_type", length = 30)
    public String jobType;

    @Column(name = "marked_by_username", length = 120)
    public String markedByUsername;

    @Column(name = "marked_at")
    public Instant markedAt;

    /** Whether bonus was included when marking this job paid */
    @Column(name = "include_bonus")
    public Boolean includeBonus;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (markedAt == null) markedAt = Instant.now();
    }
}
