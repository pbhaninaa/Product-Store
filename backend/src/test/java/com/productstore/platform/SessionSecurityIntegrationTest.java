package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Tokens must die with the access behind them, and support shadow tokens must stay contained. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionSecurityIntegrationTest {

  private static final String SLUG = "session-demo";
  private static final String SETTINGS = "/api/m/" + SLUG + "/admin/store/settings";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EmployeeRepository employeeRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired SupportAuditLogRepository auditLogs;
  @Autowired PasswordHasher passwordHasher;
  @Autowired JwtService jwtService;

  UUID tenantId;
  UUID ownerId;

  @BeforeEach
  void seed() {
    orderRepository.deleteAll();
    employeeRepository.deleteAll();
    subscriptionRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Session Demo";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    tenantId = t.id;
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    ownerId = createUser("owner-session@test.local", "Owner@123456");
    addMembership(ownerId, Role.MERCHANT_OWNER);
  }

  @Test
  void passwordChangeSignsOutOtherSessionsButKeepsThisOne() throws Exception {
    String oldToken = login("owner-session@test.local", "Owner@123456");
    mvc.perform(get(SETTINGS).header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());

    Thread.sleep(1100); // JWT iat has second precision
    String body =
        mvc.perform(
                post("/api/auth/change-password")
                    .header("Authorization", "Bearer " + oldToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"Owner@123456\",\"newPassword\":\"Changed@123456\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newToken = objectMapper.readTree(body).get("token").asText();

    mvc.perform(get(SETTINGS).header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(SETTINGS).header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
  }

  @Test
  void suspendedUserTokenStopsWorkingImmediately() throws Exception {
    String token = login("owner-session@test.local", "Owner@123456");
    UserEntity u = userRepository.findById(ownerId).orElseThrow();
    u.suspended = true;
    userRepository.save(u);

    mvc.perform(get(SETTINGS).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    // /api/auth/** is permitAll, so the rejected token reaches refresh as anonymous -> not_authenticated (403).
    mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void shadowTokenCannotRefreshOrTouchBankingAndItsWritesAreAudited() throws Exception {
    UUID supportId = createUser("support-session@test.local", "Support@123456");
    String shadow =
        jwtService.mintShadowToken(ownerId, "owner-session@test.local", tenantId, SLUG, supportId);

    mvc.perform(get(SETTINGS).header("Authorization", "Bearer " + shadow)).andExpect(status().isOk());
    mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + shadow))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/store/banking")
                .header("Authorization", "Bearer " + shadow)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankAccountNumber\":\"999\"}"))
        .andExpect(status().isForbidden());

    long before = auditLogs.count();
    mvc.perform(
        put("/api/m/" + SLUG + "/admin/store/contact")
            .header("Authorization", "Bearer " + shadow)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contactNotes\":\"hi\"}"));
    assertThat(auditLogs.count()).isEqualTo(before + 1);
    assertThat(auditLogs.findTop100ByOrderByCreatedAtDesc().get(0).actorUserId).isEqualTo(supportId);
  }

  @Test
  void deactivatedEmployeeLosesStoreAccessAndReactivationRestoresIt() throws Exception {
    String ownerToken = login("owner-session@test.local", "Owner@123456");
    String created =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"staff-session@test.local\",\"password\":\"Staff@123456\","
                            + "\"displayName\":\"Thabo\",\"role\":\"STAFF\",\"payMethod\":\"PER_SERVICE\","
                            + "\"payRate\":100,\"bonusPercentage\":0}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String employeeId = objectMapper.readTree(created).path("member").path("id").asText();
    String staffToken = login("staff-session@test.local", "Staff@123456");
    String orders = "/api/m/" + SLUG + "/admin/orders";
    mvc.perform(get(orders).header("Authorization", "Bearer " + staffToken)).andExpect(status().isOk());

    Thread.sleep(1100);
    mvc.perform(
            delete("/api/m/" + SLUG + "/admin/team/" + employeeId)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk());
    mvc.perform(get(orders).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isUnauthorized());

    mvc.perform(
            put("/api/m/" + SLUG + "/admin/team/" + employeeId)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"))
        .andExpect(status().isOk());
    String again = login("staff-session@test.local", "Staff@123456");
    mvc.perform(get(orders).header("Authorization", "Bearer " + again)).andExpect(status().isOk());
  }

  @Test
  void staffCannotCreditWorkToThemselvesAndSupportCannotDeleteMerchants() throws Exception {
    String ownerToken = login("owner-session@test.local", "Owner@123456");
    String created =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"staff-credit@test.local\",\"password\":\"Staff@123456\","
                            + "\"displayName\":\"Lerato\",\"role\":\"STAFF\",\"payMethod\":\"PER_SERVICE\","
                            + "\"payRate\":100,\"bonusPercentage\":0}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String employeeId = objectMapper.readTree(created).path("member").path("id").asText();
    String staffToken = login("staff-credit@test.local", "Staff@123456");

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/team/assign-job")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"jobId\":\"" + UUID.randomUUID() + "\",\"jobType\":\"ORDER\",\"employeeId\":\""
                        + employeeId + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + UUID.randomUUID() + "/confirm-payment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashCode\":\"123456\",\"completedByEmployeeId\":\"" + employeeId + "\"}"))
        .andExpect(status().isForbidden());

    // Owner may credit only an active employee of this store.
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + UUID.randomUUID() + "/confirm-payment")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashCode\":\"123456\",\"completedByEmployeeId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest());

    UUID supportId = createUser("support-delete@test.local", "Support@123456");
    MembershipEntity sm = new MembershipEntity();
    sm.id = UUID.randomUUID();
    sm.userId = supportId;
    sm.role = Role.SUPPORT_USER;
    sm.permissions = "MANAGE_MERCHANTS";
    sm.createdAt = Instant.now();
    membershipRepository.save(sm);
    String supportToken = login("support-delete@test.local", "Support@123456");
    mvc.perform(delete("/api/support/merchants/" + SLUG).header("Authorization", "Bearer " + supportToken))
        .andExpect(status().isForbidden());
    assertThat(tenantRepository.findBySlug(SLUG)).isPresent();
  }

  private UUID createUser(String email, String password) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash(password);
    u.createdAt = Instant.now();
    userRepository.save(u);
    return u.id;
  }

  private void addMembership(UUID userId, Role role) {
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = userId;
    m.tenantId = tenantId;
    m.role = role;
    m.createdAt = Instant.now();
    membershipRepository.save(m);
  }

  private String login(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }
}
