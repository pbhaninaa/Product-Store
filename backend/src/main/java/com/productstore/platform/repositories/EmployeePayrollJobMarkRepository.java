package com.productstore.platform.repositories;

import com.productstore.platform.entities.EmployeePayrollJobMarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeePayrollJobMarkRepository extends JpaRepository<EmployeePayrollJobMarkEntity, UUID> {

    List<EmployeePayrollJobMarkEntity> findByTenantIdAndEmployeeRecordId(UUID tenantId, UUID employeeRecordId);

    Optional<EmployeePayrollJobMarkEntity> findByTenantIdAndEmployeeRecordIdAndJobIdAndJobType(
        UUID tenantId, UUID employeeRecordId, UUID jobId, String jobType);

    void deleteByTenantIdAndEmployeeRecordIdAndJobIdAndJobType(
        UUID tenantId, UUID employeeRecordId, UUID jobId, String jobType);
}
