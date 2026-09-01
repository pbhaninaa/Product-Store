package com.productstore.platform.services;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeNotificationPublisher {
  private static final Logger log = LoggerFactory.getLogger(RealtimeNotificationPublisher.class);

  private final SimpMessagingTemplate messaging;

  public RealtimeNotificationPublisher(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  public void publishToUser(UUID userId, Map<String, Object> payload) {
    if (userId == null || payload == null) return;
    try {
      messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", payload);
    } catch (Exception e) {
      log.debug("STOMP push skipped for {}: {}", userId, e.getMessage());
    }
  }
}
