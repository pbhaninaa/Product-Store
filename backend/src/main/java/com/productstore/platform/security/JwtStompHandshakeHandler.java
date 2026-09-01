package com.productstore.platform.security;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class JwtStompHandshakeHandler extends DefaultHandshakeHandler {

  public static final String ATTR_JWT_USER_ID = "jwtUserId";

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object u = attributes.get(ATTR_JWT_USER_ID);
    if (u instanceof String userId && !userId.isBlank()) {
      return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }
    return super.determineUser(request, wsHandler, attributes);
  }
}
