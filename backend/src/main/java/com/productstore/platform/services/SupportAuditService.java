package com.productstore.platform.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.SupportAuditLogEntity;
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportAuditService {
  private final SupportAuditLogRepository logs;

  public SupportAuditService(SupportAuditLogRepository logs) {
    this.logs = logs;
  }

  @Transactional
  public void record(ApiUserPrincipal actor, String action, String entityType, String entityId, String detail) {
    SupportAuditLogEntity row = new SupportAuditLogEntity();
    if (actor != null) {
      row.actorUserId = actor.userId();
      row.actorEmail = actor.getUsername();
    }
    row.action = action == null ? "" : action;
    row.entityType = entityType;
    row.entityId = entityId;
    row.detail = detail == null ? "" : detail;
    logs.save(row);
  }

  public List<Map<String, Object>> listRecent() {
    return logs.findTop100ByOrderByCreatedAtDesc().stream().map(this::toMap).toList();
  }

  private Map<String, Object> toMap(SupportAuditLogEntity n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.id.toString());
    m.put("actorUserId", n.actorUserId != null ? n.actorUserId.toString() : null);
    m.put("actorEmail", n.actorEmail);
    m.put("action", n.action);
    m.put("entityType", n.entityType);
    m.put("entityId", n.entityId);
    m.put("detail", n.detail);
    m.put("createdAt", n.createdAt != null ? n.createdAt.toString() : null);
    return m;
  }
}
