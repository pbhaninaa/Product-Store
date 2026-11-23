package com.productstore.platform.config;

import com.productstore.platform.security.JwtHandshakeInterceptor;
import com.productstore.platform.security.JwtStompHandshakeHandler;
import com.productstore.platform.security.StompInboundGuard;
import com.productstore.platform.services.WebConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
  private final JwtStompHandshakeHandler jwtStompHandshakeHandler;
  private final StompInboundGuard stompInboundGuard;
  private final Environment env;

  public WebSocketConfig(
      JwtHandshakeInterceptor jwtHandshakeInterceptor,
      JwtStompHandshakeHandler jwtStompHandshakeHandler,
      StompInboundGuard stompInboundGuard,
      Environment env) {
    this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    this.jwtStompHandshakeHandler = jwtStompHandshakeHandler;
    this.stompInboundGuard = stompInboundGuard;
    this.env = env;
  }

  /** STOMP heartbeat interval (both directions) so idle sockets survive proxies and dead ones are detected. */
  static final long HEARTBEAT_MS = 20_000;

  private TaskScheduler messageBrokerTaskScheduler;

  /** Spring's own broker scheduler; the simple broker only sends/checks heartbeats when given one. */
  @Autowired
  public void setMessageBrokerTaskScheduler(
      @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler) {
    this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config
        .enableSimpleBroker("/queue", "/topic")
        .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
        .setTaskScheduler(messageBrokerTaskScheduler);
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompInboundGuard);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Same allowlist as HTTP CORS (WebConfig): any origin on local/SIT/test, configured frontend
    // origins on UAT/PROD. An empty list means same-origin only.
    String[] origins = WebConfig.allowedOriginPatterns(env).toArray(String[]::new);
    registry
        .addEndpoint("/ws")
        .setHandshakeHandler(jwtStompHandshakeHandler)
        .setAllowedOriginPatterns(origins)
        .addInterceptors(jwtHandshakeInterceptor)
        .withSockJS();
  }
}
