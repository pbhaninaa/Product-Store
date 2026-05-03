package com.productstore.platform.repositories;

import com.productstore.platform.entities.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {

    List<EmployeeEntity> findByTenantId(UUID tenantId);

    Optional<EmployeeEntity> findByUserProfileId(UUID userProfileId);

    List<EmployeeEntity> findByTenantIdAndIsActive(UUID tenantId, boolean isActive);
}
