package com.productstore.platform.repositories;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.SupportAuditLogEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportAuditLogRepository extends JpaRepository<SupportAuditLogEntity, UUID> {
  List<SupportAuditLogEntity> findTop100ByOrderByCreatedAtDesc();
}
