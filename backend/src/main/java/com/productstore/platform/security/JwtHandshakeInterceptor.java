package com.productstore.platform.security;

import java.util.Map;
import java.util.UUID;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.JwtService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtService jwtService;
  private final UserRepository users;

  public JwtHandshakeInterceptor(JwtService jwtService, UserRepository users) {
    this.jwtService = jwtService;
    this.users = users;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }
    HttpServletRequest http = servletRequest.getServletRequest();
    String path = http.getRequestURI();
    if (path != null && path.contains("/info")) {
      return true;
    }
    String token = http.getParameter("access_token");
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      DecodedJWT jwt = jwtService.verify(token);
      UUID userId = UUID.fromString(jwt.getSubject());
      UserEntity user = users.findById(userId).orElse(null);
      if (user == null || user.suspended) {
        return false;
      }
      attributes.put(JwtStompHandshakeHandler.ATTR_JWT_USER_ID, userId.toString());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }
}
