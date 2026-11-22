package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.SupportAuditLogEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.PasswordResetTokenRepository;
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
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

/** Support console "Users" directory: access, safety rules, suspension enforcement and audit. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportUserDirectoryIntegrationTest {
  private static final String PASSWORD = "client-password-1";

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired SupportAuditLogRepository auditRepository;
  @Autowired PasswordResetTokenRepository resetTokenRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  @BeforeEach
  void clean() {
    resetTokenRepository.deleteAll();
    auditRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  // --- fixtures -------------------------------------------------------------------------------

  private UserEntity user(String email, String displayName) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.displayName = displayName;
    u.passwordHash = passwordHasher.hash(PASSWORD);
    u.createdAt = Instant.now();
    return userRepository.save(u);
  }

  private void membership(UserEntity u, Role role, UUID tenantId, String permissionsCsv) {
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = tenantId;
    m.role = role;
    m.permissions = permissionsCsv;
    m.createdAt = Instant.now();
    membershipRepository.save(m);
  }

  private TenantEntity tenant(String slug, String name) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = name;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    return tenantRepository.save(t);
  }

  private UserEntity admin() {
    UserEntity a = user("admin@platform.test", "Platform Admin");
    membership(a, Role.PLATFORM_ADMIN, null, null);
    return a;
  }

  private UserEntity support(String email, String permissionsCsv) {
    UserEntity s = user(email, null);
    membership(s, Role.SUPPORT_USER, null, permissionsCsv);
    return s;
  }

  private UserEntity client(String email, String name) {
    UserEntity c = user(email, name);
    membership(c, Role.CLIENT, null, null);
    return c;
  }

  private String bearer(UserEntity u, Role role) {
    return "Bearer " + jwtService.mintToken(u.id, u.email, List.of(role), null, null);
  }

  private List<SupportAuditLogEntity> audits(String action) {
    return auditRepository.findAll().stream().filter(a -> action.equals(a.action)).toList();
  }

  // --- access ---------------------------------------------------------------------------------

  @Test
  void platformAdminListsEveryKindOfUserWithStoresAndNoSensitiveFields() throws Exception {
    UserEntity admin = admin();
    support("agent@support.test", "VIEW_OPS");
    client("shopper@client.test", "Sam Shopper");
    TenantEntity store = tenant("glow-salon", "Glow Salon");
    UserEntity owner = user("owner@merchant.test", "Olive Owner");
    membership(owner, Role.MERCHANT_OWNER, store.id, null);
    UserEntity staff = user("staff@merchant.test", null);
    membership(staff, Role.MERCHANT_STAFF, store.id, null);

    String body =
        mvc.perform(get("/api/support/users").header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.users.length()").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(
            get("/api/support/users")
                .param("q", "owner@merchant")
                .header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.users[0].name").value("Olive Owner"))
        .andExpect(jsonPath("$.users[0].roles[0]").value("MERCHANT_OWNER"))
        .andExpect(jsonPath("$.users[0].stores[0].slug").value("glow-salon"))
        .andExpect(jsonPath("$.users[0].stores[0].name").value("Glow Salon"))
        .andExpect(jsonPath("$.users[0].suspended").value(false))
        .andExpect(jsonPath("$.users[0].lastLoginAt").doesNotExist());

    // POPIA: only directory fields leave the server.
    assertThat(body)
        .doesNotContain("passwordHash")
        .doesNotContain("password_hash")
        .doesNotContain(owner.passwordHash)
        .doesNotContain("tokensValidAfter")
        .doesNotContain("referralCode")
        .doesNotContain("token");
  }

  @Test
  void supportWithoutViewUsersIsForbidden() throws Exception {
    UserEntity agent = support("agent@support.test", "VIEW_OPS,MANAGE_STAFF,VIEW_AUDIT");
    mvc.perform(get("/api/support/users").header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/support/users/stores").header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void defaultSupportPermissionsDoNotIncludeTheUserDirectory() throws Exception {
    // Empty CSV resolves to DEFAULT_SUPPORT: the directory is opt-in.
    UserEntity agent = support("agent@support.test", null);
    mvc.perform(get("/api/support/users").header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewUsersCanListButNotSuspendOrReset() throws Exception {
    UserEntity agent = support("agent@support.test", "VIEW_USERS");
    UserEntity shopper = client("shopper@client.test", "Sam Shopper");

    mvc.perform(get("/api/support/users").header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2));
    mvc.perform(
            post("/api/support/users/" + shopper.id + "/suspend")
                .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/support/users/" + shopper.id + "/send-password-reset")
                .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isForbidden());

    assertThat(userRepository.findById(shopper.id).orElseThrow().suspended).isFalse();
    assertThat(auditRepository.findAll()).isEmpty();
  }

  // --- suspension -----------------------------------------------------------------------------

  @Test
  void manageUsersSuspendsClientWhichBlocksSignInAndKillsExistingToken() throws Exception {
    UserEntity agent = support("agent@support.test", "VIEW_USERS,MANAGE_USERS");
    UserEntity shopper = client("shopper@client.test", "Sam Shopper");
    String shopperToken = bearer(shopper, Role.CLIENT);
    String login = "{\"email\":\"shopper@client.test\",\"password\":\"" + PASSWORD + "\"}";

    // Before: the client can sign in (which stamps last sign-in) and use their token.
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
        .andExpect(status().isOk());
    assertThat(userRepository.findById(shopper.id).orElseThrow().lastLoginAt).isNotNull();
    mvc.perform(get("/api/clients/me/orders").header("Authorization", shopperToken))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/support/users/" + shopper.id + "/suspend")
                .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suspended").value(true))
        .andExpect(jsonPath("$.lastLoginAt").exists());

    assertThat(userRepository.findById(shopper.id).orElseThrow().suspended).isTrue();
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("account_suspended"));
    mvc.perform(get("/api/clients/me/orders").header("Authorization", shopperToken))
        .andExpect(status().isUnauthorized());

    List<SupportAuditLogEntity> rows = audits("USER_SUSPEND");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).actorUserId).isEqualTo(agent.id);
    assertThat(rows.get(0).actorEmail).isEqualTo("agent@support.test");
    assertThat(rows.get(0).entityType).isEqualTo("USER");
    assertThat(rows.get(0).entityId).isEqualTo(shopper.id.toString());

    // Reactivate: the client can sign in again; the pre-suspension token stays revoked.
    mvc.perform(
            post("/api/support/users/" + shopper.id + "/reactivate")
                .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suspended").value(false));
    assertThat(audits("USER_REACTIVATE")).hasSize(1);
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
        .andExpect(status().isOk());
  }

  @Test
  void suspendedMerchantOwnerCannotUseMerchantAdmin() throws Exception {
    UserEntity admin = admin();
    TenantEntity store = tenant("glow-salon", "Glow Salon");
    UserEntity owner = user("owner@merchant.test", "Olive Owner");
    membership(owner, Role.MERCHANT_OWNER, store.id, null);
    String ownerToken =
        "Bearer "
            + jwtService.mintToken(owner.id, owner.email, List.of(Role.MERCHANT_OWNER), store.id, store.slug);
    // Before suspension the token authenticates (whatever the endpoint then decides).
    mvc.perform(get("/api/m/glow-salon/admin/products").header("Authorization", ownerToken))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isNotEqualTo(401));

    mvc.perform(
            post("/api/support/users/" + owner.id + "/suspend")
                .header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
        .andExpect(status().isOk());

    mvc.perform(get("/api/m/glow-salon/admin/products").header("Authorization", ownerToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@merchant.test\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("account_suspended"));
  }

  @Test
  void cannotSuspendSelf() throws Exception {
    UserEntity admin = admin();
    mvc.perform(
            post("/api/support/users/" + admin.id + "/suspend")
                .header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("cannot_suspend_self"));
    assertThat(userRepository.findById(admin.id).orElseThrow().suspended).isFalse();
  }

  @Test
  void supportCannotSuspendOrResetPlatformAdminOrOtherSupport() throws Exception {
    UserEntity admin = admin();
    UserEntity agent = support("agent@support.test", "VIEW_USERS,MANAGE_USERS");
    UserEntity colleague = support("colleague@support.test", "VIEW_OPS");

    for (UserEntity target : List.of(admin, colleague)) {
      mvc.perform(
              post("/api/support/users/" + target.id + "/suspend")
                  .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/support/users/" + target.id + "/send-password-reset")
                  .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
          .andExpect(status().isForbidden());
      assertThat(userRepository.findById(target.id).orElseThrow().suspended).isFalse();
    }

    // A platform admin may act on a support user.
    mvc.perform(
            post("/api/support/users/" + colleague.id + "/suspend")
                .header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suspended").value(true));
  }

  @Test
  void unknownUserIsNotFound() throws Exception {
    UserEntity admin = admin();
    mvc.perform(
            post("/api/support/users/" + UUID.randomUUID() + "/suspend")
                .header("Authorization", bearer(admin, Role.PLATFORM_ADMIN)))
        .andExpect(status().isNotFound());
  }

  // --- password reset -------------------------------------------------------------------------

  @Test
  void manageUsersSendsPasswordResetAndAuditsIt() throws Exception {
    UserEntity agent = support("agent@support.test", "MANAGE_USERS");
    UserEntity shopper = client("shopper@client.test", "Sam Shopper");

    mvc.perform(
            post("/api/support/users/" + shopper.id + "/send-password-reset")
                .header("Authorization", bearer(agent, Role.SUPPORT_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(resetTokenRepository.findAll()).anyMatch(t -> shopper.id.equals(t.userId));
    List<SupportAuditLogEntity> rows = audits("USER_PASSWORD_RESET_SENT");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).actorUserId).isEqualTo(agent.id);
    assertThat(rows.get(0).entityId).isEqualTo(shopper.id.toString());
  }

  // --- search / filters / pagination ----------------------------------------------------------

  @Test
  void filtersByRoleStoreStatusAndSearchAndPaginates() throws Exception {
    UserEntity admin = admin();
    TenantEntity glow = tenant("glow-salon", "Glow Salon");
    TenantEntity bolt = tenant("bolt-hardware", "Bolt Hardware");
    UserEntity glowOwner = user("owner@glow.test", "Grace Glow");
    membership(glowOwner, Role.MERCHANT_OWNER, glow.id, null);
    UserEntity boltOwner = user("owner@bolt.test", "Ben Bolt");
    membership(boltOwner, Role.MERCHANT_OWNER, bolt.id, null);
    for (int i = 0; i < 7; i++) client("client" + i + "@client.test", "Client " + i);
    UserEntity suspended = client("gone@client.test", "Gone Client");
    suspended.suspended = true;
    userRepository.save(suspended);
    String auth = bearer(admin, Role.PLATFORM_ADMIN);

    mvc.perform(get("/api/support/users").param("role", "CLIENT").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(8));
    mvc.perform(get("/api/support/users").param("store", "glow-salon").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.users[0].email").value("owner@glow.test"));
    mvc.perform(get("/api/support/users").param("store", "no-such-store").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/support/users").param("status", "suspended").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.users[0].email").value("gone@client.test"));
    mvc.perform(
            get("/api/support/users")
                .param("status", "active")
                .param("role", "CLIENT")
                .header("Authorization", auth))
        .andExpect(jsonPath("$.total").value(7));
    // Search matches the display name, case-insensitively.
    mvc.perform(get("/api/support/users").param("q", "BEN BO").header("Authorization", auth))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.users[0].email").value("owner@bolt.test"));
    // LIKE wildcards in the search box are literal.
    mvc.perform(get("/api/support/users").param("q", "%").header("Authorization", auth))
        .andExpect(jsonPath("$.total").value(0));

    // 11 users in total: pages of 4 -> 4, 4, 3.
    mvc.perform(get("/api/support/users").param("size", "4").param("page", "2").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(11))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.users.length()").value(3));
    // Page size is capped.
    mvc.perform(get("/api/support/users").param("size", "5000").header("Authorization", auth))
        .andExpect(jsonPath("$.size").value(100));

    mvc.perform(get("/api/support/users").param("role", "WIZARD").header("Authorization", auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_role"));

    mvc.perform(get("/api/support/users/stores").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stores.length()").value(2))
        .andExpect(jsonPath("$.stores[0].slug").value("bolt-hardware"));
  }
}
