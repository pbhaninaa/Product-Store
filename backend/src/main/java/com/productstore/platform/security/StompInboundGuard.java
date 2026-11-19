package com.productstore.platform.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Inbound STOMP guard. Clients only ever receive their own notifications on
 * {@code /user/queue/notifications}, so:
 *
 * <ul>
 *   <li>CONNECT requires the principal established by the JWT handshake;
 *   <li>SEND straight to broker destinations ({@code /topic}, {@code /queue}, {@code /user}) is
 *       refused, so a client cannot publish to other users' queues;
 *   <li>SUBSCRIBE is only allowed to {@code /user/...} destinations (resolved per session user).
 * </ul>
 */
@Component
public class StompInboundGuard implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      accessor = StompHeaderAccessor.wrap(message);
    }
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }
    String destination = accessor.getDestination();
    switch (command) {
      case CONNECT, STOMP -> {
        if (accessor.getUser() == null) {
          throw new MessageDeliveryException(message, "not_authenticated");
        }
      }
      case SEND -> {
        if (destination == null || isBrokerDestination(destination)) {
          throw new MessageDeliveryException(message, "forbidden_destination");
        }
      }
      case SUBSCRIBE -> {
        if (accessor.getUser() == null) {
          throw new MessageDeliveryException(message, "not_authenticated");
        }
        if (destination == null || !destination.startsWith("/user/")) {
          throw new MessageDeliveryException(message, "forbidden_destination");
        }
      }
      default -> {
        // DISCONNECT, UNSUBSCRIBE, ACK, NACK, heartbeats: allowed
      }
    }
    return message;
  }

  private static boolean isBrokerDestination(String destination) {
    return destination.startsWith("/topic")
        || destination.startsWith("/queue")
        || destination.startsWith("/user");
  }
}
