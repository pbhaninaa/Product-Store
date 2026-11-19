package com.productstore.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class StompInboundGuardTest {
  private final StompInboundGuard guard = new StompInboundGuard();
  private final Principal user =
      new UsernamePasswordAuthenticationToken("user-1", null, Collections.emptyList());

  private static Message<byte[]> frame(StompCommand command, String destination, Principal user) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    if (destination != null) accessor.setDestination(destination);
    if (command == StompCommand.SUBSCRIBE) accessor.setSubscriptionId("sub-0");
    accessor.setSessionId("s1");
    accessor.setUser(user);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Test
  void allowsAuthenticatedConnectAndOwnUserQueueSubscription() {
    var connect = frame(StompCommand.CONNECT, null, user);
    assertThat(guard.preSend(connect, null)).isSameAs(connect);
    var sub = frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", user);
    assertThat(guard.preSend(sub, null)).isSameAs(sub);
  }

  @Test
  void rejectsUnauthenticatedConnectAndSubscribe() {
    assertThatThrownBy(() -> guard.preSend(frame(StompCommand.CONNECT, null, null), null))
        .isInstanceOf(MessageDeliveryException.class);
    assertThatThrownBy(
            () ->
                guard.preSend(
                    frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", null), null))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void rejectsSubscribeOutsideUserDestinations() {
    for (String dest :
        new String[] {"/topic/anything", "/queue/notifications", "/queue/notifications-user123", "/app/x"}) {
      assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SUBSCRIBE, dest, user), null))
          .as(dest)
          .isInstanceOf(MessageDeliveryException.class);
    }
  }

  @Test
  void rejectsClientSendToBrokerDestinations() {
    for (String dest :
        new String[] {"/topic/x", "/queue/notifications", "/user/other/queue/notifications"}) {
      assertThatThrownBy(() -> guard.preSend(frame(StompCommand.SEND, dest, user), null))
          .as(dest)
          .isInstanceOf(MessageDeliveryException.class);
    }
    var app = frame(StompCommand.SEND, "/app/ping", user);
    assertThat(guard.preSend(app, null)).isSameAs(app);
  }
}
