package com.productstore.platform.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.InAppNotificationEntity;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.repositories.InAppNotificationRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InAppNotificationService {
  private final InAppNotificationRepository notifications;
  private final MembershipRepository memberships;

  public InAppNotificationService(
      InAppNotificationRepository notifications, MembershipRepository memberships) {
    this.notifications = notifications;
    this.memberships = memberships;
  }

  @Transactional
  public void notifyTenantStaff(
      UUID tenantId, String title, String body, String type, String referenceType, String referenceId) {
    if (tenantId == null) return;
    List<MembershipEntity> members =
        memberships.findAllByTenantIdAndRoleIn(
            tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF));
    for (MembershipEntity m : members) {
      notifyUser(m.userId, tenantId, title, body, type, referenceType, referenceId);
    }
  }

  @Transactional
  public void notifyUser(
      UUID userId,
      UUID tenantId,
      String title,
      String body,
      String type,
      String referenceType,
      String referenceId) {
    if (userId == null) return;
    InAppNotificationEntity n = new InAppNotificationEntity();
    n.userId = userId;
    n.tenantId = tenantId;
    n.title = title == null ? "" : title;
    n.body = body == null ? "" : body;
    n.notificationType = type == null ? "INFO" : type;
    n.referenceType = referenceType;
    n.referenceId = referenceId;
    n.isRead = false;
    notifications.save(n);
  }

  public List<Map<String, Object>> listForUser(UUID userId, UUID tenantId) {
    List<InAppNotificationEntity> rows =
        notifications.findByUserIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (InAppNotificationEntity n : rows) {
      out.add(toMap(n));
    }
    return out;
  }

  public long unreadCount(UUID userId, UUID tenantId) {
    return notifications.countByUserIdAndTenantIdAndIsReadFalse(userId, tenantId);
  }

  @Transactional
  public boolean markRead(UUID userId, UUID tenantId, UUID notificationId) {
    InAppNotificationEntity n =
        notifications.findById(notificationId).orElse(null);
    if (n == null || !n.userId.equals(userId)) return false;
    if (tenantId != null && n.tenantId != null && !tenantId.equals(n.tenantId)) return false;
    n.isRead = true;
    n.readAt = Instant.now();
    notifications.save(n);
    return true;
  }

  @Transactional
  public int markAllRead(UUID userId, UUID tenantId) {
    int n = 0;
    for (InAppNotificationEntity row :
        notifications.findByUserIdAndTenantIdOrderByCreatedAtDesc(userId, tenantId)) {
      if (!row.isRead) {
        row.isRead = true;
        row.readAt = Instant.now();
        notifications.save(row);
        n++;
      }
    }
    return n;
  }

  @Transactional
  public void notifyPlatformStaff(
      String title, String body, String type, String referenceType, String referenceId) {
    List<MembershipEntity> staff =
        memberships.findAllByRoleIn(List.of(Role.SUPPORT_USER, Role.PLATFORM_ADMIN));
    for (MembershipEntity m : staff) {
      notifyUser(m.userId, null, title, body, type, referenceType, referenceId);
    }
  }

  public List<Map<String, Object>> listForPlatformUser(UUID userId) {
    List<InAppNotificationEntity> rows =
        notifications.findByUserIdAndTenantIdIsNullOrderByCreatedAtDesc(userId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (InAppNotificationEntity n : rows) {
      out.add(toMap(n));
    }
    return out;
  }

  public long unreadCountPlatform(UUID userId) {
    return notifications.countByUserIdAndTenantIdIsNullAndIsReadFalse(userId);
  }

  @Transactional
  public int markAllReadPlatform(UUID userId) {
    int n = 0;
    for (InAppNotificationEntity row :
        notifications.findByUserIdAndTenantIdIsNullOrderByCreatedAtDesc(userId)) {
      if (!row.isRead) {
        row.isRead = true;
        row.readAt = Instant.now();
        notifications.save(row);
        n++;
      }
    }
    return n;
  }

  private static Map<String, Object> toMap(InAppNotificationEntity n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.id.toString());
    m.put("title", n.title);
    m.put("body", n.body);
    m.put("notificationType", n.notificationType);
    m.put("referenceType", n.referenceType);
    m.put("referenceId", n.referenceId);
    m.put("read", n.isRead);
    m.put("createdAt", n.createdAt != null ? n.createdAt.toString() : null);
    return m;
  }
}
