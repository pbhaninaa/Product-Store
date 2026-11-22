package com.productstore.platform.services;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes an in-app notification to the recipient's open sessions on {@code
 * /user/queue/notifications} (STOMP). The STOMP principal name is the user id (see {@code
 * JwtStompHandshakeHandler}).
 *
 * <p>Inside a transaction the push is deferred until after commit, so a rolled-back notification
 * is never delivered and a client that refetches on receipt always sees the new row. A failed push
 * never affects the business action: clients fall back to slow polling.
 */
@Service
public class RealtimeNotificationPublisher {
  static final String DESTINATION = "/queue/notifications";

  private static final Logger log = LoggerFactory.getLogger(RealtimeNotificationPublisher.class);

  private final SimpMessagingTemplate messaging;

  public RealtimeNotificationPublisher(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  public void publishToUser(UUID userId, Map<String, Object> payload) {
    if (userId == null || payload == null) return;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send(userId, payload);
            }
          });
      return;
    }
    send(userId, payload);
  }

  private void send(UUID userId, Map<String, Object> payload) {
    try {
      messaging.convertAndSendToUser(userId.toString(), DESTINATION, payload);
    } catch (Exception e) {
      // No user id or content in the log: only the failure type.
      log.warn("Realtime notification push failed: {}", e.getClass().getSimpleName());
    }
  }
}
