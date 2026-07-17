package com.productstore.platform.repositories;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.SupportTicketEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID> {
  List<SupportTicketEntity> findByStatusOrderByCreatedAtDesc(SupportTicketEntity.Status status);

  List<SupportTicketEntity> findTop100ByOrderByCreatedAtDesc();

  List<SupportTicketEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  long countByStatus(SupportTicketEntity.Status status);
}
