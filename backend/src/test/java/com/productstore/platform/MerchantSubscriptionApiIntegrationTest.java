package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantSubscriptionApiIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired PasswordHasher passwordHasher;

  UUID tenantId;
  String ownerToken;

  @BeforeEach
  void seed() throws Exception {
    subscriptionRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "sub-demo";
    t.name = "Sub Demo";
    t.modulesJson = "{}";
    t.subscriptionPlan = TenantEntity.SubscriptionPlan.STARTER;
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    tenantId = t.id;

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-sub@test.local";
    owner.passwordHash = passwordHasher.hash("Owner@123456");
    owner.createdAt = Instant.now();
    userRepository.save(owner);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = owner.id;
    m.tenantId = t.id;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    membershipRepository.save(m);

    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"owner-sub@test.local\",\"password\":\"Owner@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    ownerToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
  }

  @Test
  void choosePlan_thenForceActivate_grantsFeatures() throws Exception {
    mvc.perform(
            get("/api/m/sub-demo/admin/subscription/me")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.needsPlanSelection").value(false));

    MvcResult chosen =
        mvc.perform(
                put("/api/m/sub-demo/admin/subscription/plan")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tier\":\"STANDARD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.planTier").value("STANDARD"))
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.needsPaymentProofUpload").value(true))
            .andReturn();
    JsonNode st = objectMapper.readTree(chosen.getResponse().getContentAsString());
    assertThat(st.path("mandatoryPaymentReference").asText()).isNotBlank();
    assertThat(st.path("subscriptionFee").asDouble()).isEqualTo(199.0);

    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    mvc.perform(
            get("/api/m/sub-demo/admin/subscription/me")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.features.insights").value(true))
        .andExpect(jsonPath("$.features.payroll").value(true))
        .andExpect(jsonPath("$.features.whatsapp").value(false));

    mvc.perform(
            post("/api/m/sub-demo/admin/team")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"staff-sub@test.local",
                      "password":"Staff@123456",
                      "displayName":"Sub Staff",
                      "role":"STAFF",
                      "payMethod":"PER_SERVICE",
                      "payRate":100,
                      "bonusPercentage":0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
  }

  @Test
  void starterActive_blocksPayrollFeature() throws Exception {
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STARTER);

    mvc.perform(
            get("/api/m/sub-demo/admin/team/payment-calculations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("payroll_requires_plan"));
  }
}
