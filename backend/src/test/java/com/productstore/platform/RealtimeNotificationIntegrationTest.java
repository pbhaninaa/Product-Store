package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.InAppNotificationService;
import com.productstore.platform.services.SupportTicketService;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * In-app notifications reach the recipient's open STOMP session as soon as the creating
 * transaction commits; never other users, never rolled-back notifications, never unauthenticated
 * sockets.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeNotificationIntegrationTest {
  private static final long ARRIVAL_SECONDS = 5;
  private static final long SILENCE_MILLIS = 1500;

  @LocalServerPort int port;
  @Autowired TestRestTemplate http;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired TenantRepository tenants;
  @Autowired JwtService jwt;
  @Autowired PasswordHasher passwords;
  @Autowired InAppNotificationService notifications;
  @Autowired SupportTicketService tickets;
  @Autowired TransactionTemplate tx;

  private final List<StompSession> sessions = new ArrayList<>();
  private WebSocketStompClient stomp;
  private TenantEntity tenant;

  @BeforeEach
  void setUp() {
    stomp = new WebSocketStompClient(new StandardWebSocketClient());
    stomp.setMessageConverter(new MappingJackson2MessageConverter());
    tenant = new TenantEntity();
    tenant.id = UUID.randomUUID();
    tenant.slug = "rt" + Long.toHexString(System.nanoTime());
    tenant.name = "Realtime Store";
    tenant.modulesJson = "{}";
    tenant.createdAt = Instant.now();
    tenants.save(tenant);
  }

  @AfterEach
  void tearDown() {
    for (StompSession s : sessions) {
      if (s.isConnected()) s.disconnect();
    }
    stomp.stop();
  }

  @Test
  void committedNotificationIsPushedOnlyToItsRecipient() throws Exception {
    UUID owner = user(Role.MERCHANT_OWNER, tenant.id);
    UUID colleague = user(Role.MERCHANT_OWNER, tenant.id);
    BlockingQueue<Map<String, Object>> ownerInbox = subscribe(tokenFor(owner, Role.MERCHANT_OWNER));
    BlockingQueue<Map<String, Object>> colleagueInbox = subscribe(tokenFor(colleague, Role.MERCHANT_OWNER));

    notifications.notifyUser(owner, tenant.id, "Payroll paid", "A job was paid.", "PAYROLL_PAID", "JOB", "j1");

    Map<String, Object> msg = ownerInbox.poll(ARRIVAL_SECONDS, TimeUnit.SECONDS);
    assertThat(msg).as("owner receives the push").isNotNull();
    assertThat(msg.get("title")).isEqualTo("Payroll paid");
    assertThat(msg.get("notificationType")).isEqualTo("PAYROLL_PAID");
    assertThat(msg.get("read")).isEqualTo(false);
    assertThat(msg.get("id")).isNotNull();
    assertThat(msg.get("tenantId")).isEqualTo(tenant.id.toString());
    assertThat(msg.get("tenantSlug")).isEqualTo(tenant.slug);
    assertThat(((Number) msg.get("unread")).intValue()).isEqualTo(1);

    assertThat(colleagueInbox.poll(SILENCE_MILLIS, TimeUnit.MILLISECONDS))
        .as("another user of the same store gets nothing")
        .isNull();
  }

  @Test
  void rolledBackNotificationIsNeverPushed() throws Exception {
    UUID owner = user(Role.MERCHANT_OWNER, tenant.id);
    BlockingQueue<Map<String, Object>> inbox = subscribe(tokenFor(owner, Role.MERCHANT_OWNER));

    tx.executeWithoutResult(
        status -> {
          notifications.notifyUser(owner, tenant.id, "Ghost", "rolled back", "INFO", null, null);
          status.setRollbackOnly();
        });

    assertThat(inbox.poll(SILENCE_MILLIS, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void supportActionsPushToMerchantAndPlatformInboxes() throws Exception {
    UUID owner = user(Role.MERCHANT_OWNER, tenant.id);
    UUID admin = user(Role.PLATFORM_ADMIN, null);
    BlockingQueue<Map<String, Object>> ownerInbox = subscribe(tokenFor(owner, Role.MERCHANT_OWNER));
    BlockingQueue<Map<String, Object>> adminInbox = subscribe(tokenFor(admin, Role.PLATFORM_ADMIN));

    // Merchant opens a ticket: platform staff are told (platform inbox: no tenant).
    Map<String, Object> ticket = tickets.createForMerchant(tenant.id, owner, "Help", "Please help");
    Map<String, Object> staffMsg = awaitType(adminInbox, "SUPPORT_TICKET");
    assertThat(staffMsg.get("tenantId")).isNull();
    assertThat(staffMsg.get("tenantSlug")).isNull();
    assertThat(((Number) staffMsg.get("unread")).intValue()).isGreaterThanOrEqualTo(1);

    // Support resolves it over HTTP: the merchant who opened it is told, scoped to the store.
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenFor(admin, Role.PLATFORM_ADMIN));
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> res =
        http.postForEntity(
            "/api/support/tickets/" + ticket.get("id") + "/resolve",
            new HttpEntity<>(Map.of("note", "Sorted"), headers),
            String.class);
    assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody()).isTrue();

    Map<String, Object> resolved = awaitType(ownerInbox, "SUPPORT_TICKET_RESOLVED");
    assertThat(resolved.get("tenantSlug")).isEqualTo(tenant.slug);
    assertThat(resolved.get("referenceId")).isEqualTo(ticket.get("id"));
  }

  @Test
  void connectWithoutValidTokenIsRejected() {
    assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> connect("not-a-jwt")).isInstanceOf(Exception.class);
  }

  @Test
  void brokerNegotiatesHeartbeatsSoIdleSocketsSurviveProxies() throws Exception {
    UUID owner = user(Role.MERCHANT_OWNER, tenant.id);
    ThreadPoolTaskScheduler clientScheduler = new ThreadPoolTaskScheduler();
    clientScheduler.initialize();
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setTaskScheduler(clientScheduler);
    client.setDefaultHeartbeat(new long[] {20_000, 20_000});
    try {
      CompletableFuture<StompHeaders> connected = new CompletableFuture<>();
      StompSession s =
          client
              .connectAsync(
                  "ws://localhost:" + port + "/ws/websocket?access_token=" + tokenFor(owner, Role.MERCHANT_OWNER),
                  new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                      connected.complete(headers);
                    }
                  })
              .get(ARRIVAL_SECONDS, TimeUnit.SECONDS);
      sessions.add(s);
      assertThat(connected.get(ARRIVAL_SECONDS, TimeUnit.SECONDS).getHeartbeat()).containsExactly(20_000L, 20_000L);
    } finally {
      client.stop();
      clientScheduler.shutdown();
    }
  }

  // ---------------------------------------------------------------- helpers

  private UUID user(Role role, UUID tenantId) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = "rt-" + u.id + "@test.local";
    u.passwordHash = passwords.hash("unused");
    u.createdAt = Instant.now();
    users.save(u);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = tenantId;
    m.role = role;
    m.createdAt = Instant.now();
    memberships.save(m);
    return u.id;
  }

  private String tokenFor(UUID userId, Role role) {
    UUID tenantId = role == Role.PLATFORM_ADMIN ? null : tenant.id;
    String slug = role == Role.PLATFORM_ADMIN ? null : tenant.slug;
    return jwt.mintToken(userId, "rt-" + userId + "@test.local", List.of(role), tenantId, slug);
  }

  private StompSession connect(String token) throws Exception {
    String url = "ws://localhost:" + port + "/ws/websocket" + (token == null ? "" : "?access_token=" + token);
    StompSession s =
        stomp.connectAsync(url, new StompSessionHandlerAdapter() {}).get(ARRIVAL_SECONDS, TimeUnit.SECONDS);
    sessions.add(s);
    return s;
  }

  private BlockingQueue<Map<String, Object>> subscribe(String token) throws Exception {
    StompSession session = connect(token);
    BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
    StompSession.Subscription sub =
        session.subscribe(
            "/user/queue/notifications",
            new StompFrameHandler() {
              @Override
              public Type getPayloadType(StompHeaders headers) {
                return Map.class;
              }

              @Override
              @SuppressWarnings("unchecked")
              public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
              }
            });
    assertThat(sub.getSubscriptionId()).isNotBlank();
    // The SUBSCRIBE frame is processed asynchronously; give the broker a moment to register it.
    Thread.sleep(300);
    return inbox;
  }

  private static Map<String, Object> awaitType(BlockingQueue<Map<String, Object>> inbox, String type)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ARRIVAL_SECONDS);
    while (System.nanoTime() < deadline) {
      Map<String, Object> m = inbox.poll(200, TimeUnit.MILLISECONDS);
      if (m != null && type.equals(m.get("notificationType"))) return m;
    }
    throw new AssertionError("no " + type + " push within " + ARRIVAL_SECONDS + "s");
  }
}
