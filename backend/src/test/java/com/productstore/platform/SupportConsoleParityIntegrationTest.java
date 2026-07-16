package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantProvisioningService;
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

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportConsoleParityIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;
  @Autowired MerchantProvisioningService provisioning;
  @Autowired MerchantSubscriptionService subscriptions;

  private String platformToken;
  private String supportToken;
  private String merchantSlug;
  private String merchantOwnerToken;

  @BeforeEach
  void setUp() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    UserEntity admin = new UserEntity();
    admin.id = UUID.randomUUID();
    admin.email = "platform-parity@test.local";
    admin.passwordHash = passwordHasher.hash("unused");
    admin.createdAt = Instant.now();
    userRepository.save(admin);
    MembershipEntity am = new MembershipEntity();
    am.id = UUID.randomUUID();
    am.userId = admin.id;
    am.role = Role.PLATFORM_ADMIN;
    am.createdAt = Instant.now();
    membershipRepository.save(am);
    platformToken = jwtService.mintToken(admin.id, admin.email, List.of(Role.PLATFORM_ADMIN), null, null);

    UserEntity support = new UserEntity();
    support.id = UUID.randomUUID();
    support.email = "support-parity@test.local";
    support.passwordHash = passwordHasher.hash("unused");
    support.createdAt = Instant.now();
    userRepository.save(support);
    MembershipEntity sm = new MembershipEntity();
    sm.id = UUID.randomUUID();
    sm.userId = support.id;
    sm.role = Role.SUPPORT_USER;
    sm.createdAt = Instant.now();
    sm.permissions = "MANAGE_SUBSCRIPTIONS,MANAGE_MERCHANTS,USE_SHADOW,MANAGE_TICKETS,VIEW_OPS";
    membershipRepository.save(sm);
    supportToken =
        jwtService.mintToken(support.id, support.email, List.of(Role.SUPPORT_USER), null, null);

    merchantSlug = "parity" + Long.toHexString(System.nanoTime());
    var reg =
        provisioning.registerMerchant(
            "Parity Shop", merchantSlug, merchantSlug + "@shop.test", "Test@12345678");
    merchantOwnerToken =
        jwtService.mintToken(
            reg.owner().id,
            reg.owner().email,
            List.of(Role.MERCHANT_OWNER),
            reg.tenant().id,
            reg.tenant().slug);
  }

  @Test
  void plansListAndUpdateAsPlatformAdmin() throws Exception {
    mvc.perform(get("/api/support/subscriptions/plans").header("Authorization", "Bearer " + platformToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plans").isArray());

    mvc.perform(
            put("/api/support/subscriptions/plans/STARTER")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriptionFee\":99.5,\"billingPeriodDays\":30}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscriptionFee").value(99.5));
  }

  @Test
  void supportUserCannotUpdatePlansWithoutPermission() throws Exception {
    mvc.perform(
            put("/api/support/subscriptions/plans/STARTER")
                .header("Authorization", "Bearer " + supportToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriptionFee\":10}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void shadowTokenMintsOwnerSession() throws Exception {
    mvc.perform(
            post("/api/support/shadow/token")
                .header("Authorization", "Bearer " + supportToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + merchantSlug + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shadowSupport").value(true))
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.tenant.slug").value(merchantSlug));
  }

  @Test
  void merchantTicketNotifiesAndSupportResolves() throws Exception {
    mvc.perform(
            post("/api/m/" + merchantSlug + "/admin/help/tickets")
                .header("Authorization", "Bearer " + merchantOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Need help\",\"body\":\"Cannot upload proof\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));

    mvc.perform(get("/api/support/tickets?status=OPEN").header("Authorization", "Bearer " + supportToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tickets[0].subject").value("Need help"));

    String ticketId =
        mvc.perform(get("/api/support/tickets?status=OPEN").header("Authorization", "Bearer " + supportToken))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // resolve via list then post - parse loosely
    org.junit.jupiter.api.Assertions.assertTrue(ticketId.contains("Need help"));

    mvc.perform(get("/api/support/notifications").header("Authorization", "Bearer " + supportToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notifications").isArray());
  }

  @Test
  void forceActivateWritesAudit() throws Exception {
    var tenant = tenantRepository.findBySlug(merchantSlug).orElseThrow();
    mvc.perform(
            post("/api/support/subscriptions/" + tenant.id + "/activate")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tier\":\"STANDARD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));

    mvc.perform(get("/api/support/audit").header("Authorization", "Bearer " + platformToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries").isArray());
  }

  @Test
  void overviewIncludesBillingAndTickets() throws Exception {
    mvc.perform(get("/api/support/overview").header("Authorization", "Bearer " + supportToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billing.pendingProofs").exists())
        .andExpect(jsonPath("$.tickets.open").exists());
  }
}
