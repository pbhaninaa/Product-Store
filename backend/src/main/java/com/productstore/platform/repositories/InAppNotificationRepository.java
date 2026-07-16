package com.productstore.platform.repositories;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.InAppNotificationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InAppNotificationRepository extends JpaRepository<InAppNotificationEntity, UUID> {
  List<InAppNotificationEntity> findByUserIdAndTenantIdOrderByCreatedAtDesc(UUID userId, UUID tenantId);

  long countByUserIdAndTenantIdAndIsReadFalse(UUID userId, UUID tenantId);

  List<InAppNotificationEntity> findByUserIdAndTenantIdIsNullOrderByCreatedAtDesc(UUID userId);

  long countByUserIdAndTenantIdIsNullAndIsReadFalse(UUID userId);
}
