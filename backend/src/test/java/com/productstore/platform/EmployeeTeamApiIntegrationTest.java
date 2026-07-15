package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
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
class EmployeeTeamApiIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EmployeeRepository employeeRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired PasswordHasher passwordHasher;
  @Autowired com.productstore.platform.services.MerchantSubscriptionService subscriptions;
  @Autowired com.productstore.platform.repositories.MerchantSubscriptionRepository subscriptionRepository;

  UUID tenantId;
  String ownerToken;

  @BeforeEach
  void seed() throws Exception {
    orderRepository.deleteAll();
    employeeRepository.deleteAll();
    subscriptionRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "team-demo";
    t.name = "Team Demo";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    tenantId = t.id;

    // STANDARD unlocks payroll (Wheel Hub Gold equivalent).
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-team@test.local";
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
                        "{\"email\":\"owner-team@test.local\",\"password\":\"Owner@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    ownerToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
  }

  @Test
  void ownerCreatesStaff_calcAndMarkPaid_staffSeesIncome() throws Exception {
    MvcResult create =
        mvc.perform(
                post("/api/m/team-demo/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"staff-team@test.local",
                          "password":"Staff@123456",
                          "displayName":"Thabo",
                          "role":"STAFF",
                          "payMethod":"PER_SERVICE",
                          "payRate":150,
                          "bonusPercentage":0
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.member.email").value("staff-team@test.local"))
            .andReturn();

    String employeeId =
        objectMapper.readTree(create.getResponse().getContentAsString()).path("member").path("id").asText();

    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.createdAt = Instant.now();
    o.customerName = "C";
    o.customerEmail = "c@test.local";
    o.deliveryType = OrderEntity.DeliveryType.pickup;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.status = OrderEntity.OrderStatus.paid;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    o.subtotalZar = new BigDecimal("100.00");
    o.totalZar = new BigDecimal("100.00");
    o.paymentConfirmedAt = Instant.now();
    o.completedAt = Instant.now();
    o.completedByEmployeeId = UUID.fromString(employeeId);
    orderRepository.save(o);

    mvc.perform(
            get("/api/m/team-demo/admin/team/payment-calculations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculations[0].pendingExpected").value(150.0))
        .andExpect(jsonPath("$.calculations[0].jobCount").value(1));

    mvc.perform(
            post("/api/m/team-demo/admin/team/payroll-marks")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "employeeId",
                            employeeId,
                            "jobId",
                            o.id.toString(),
                            "jobType",
                            "ORDER",
                            "includeBonus",
                            false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mvc.perform(
            get("/api/m/team-demo/admin/team/payment-calculations")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculations[0].pendingExpected").value(0.0))
        .andExpect(jsonPath("$.calculations[0].jobCount").value(0));

    MvcResult staffLogin =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"staff-team@test.local\",\"password\":\"Staff@123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("MERCHANT_STAFF"))
            .andReturn();
    String staffToken =
        objectMapper.readTree(staffLogin.getResponse().getContentAsString()).get("token").asText();

    MvcResult income =
        mvc.perform(
                get("/api/m/team-demo/admin/team/my-expected-income")
                    .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode incomeJson = objectMapper.readTree(income.getResponse().getContentAsString());
    assertThat(incomeJson.path("pendingExpected").asDouble()).isEqualTo(0.0);
    assertThat(incomeJson.path("lines").isArray()).isTrue();
    assertThat(incomeJson.path("lines").get(0).path("employerPaid").asBoolean()).isTrue();
  }

  @Test
  void staffCannotCreateTeamMembers() throws Exception {
    mvc.perform(
            post("/api/m/team-demo/admin/team")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"staff2@test.local",
                      "password":"Staff@123456",
                      "displayName":"Lee",
                      "payMethod":"PER_SERVICE",
                      "payRate":50
                    }
                    """))
        .andExpect(status().isOk());

    MvcResult staffLogin =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"staff2@test.local\",\"password\":\"Staff@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String staffToken =
        objectMapper.readTree(staffLogin.getResponse().getContentAsString()).get("token").asText();

    mvc.perform(
            post("/api/m/team-demo/admin/team")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"other@test.local",
                      "password":"Staff@123456",
                      "displayName":"X",
                      "payMethod":"PER_SERVICE",
                      "payRate":10
                    }
                    """))
        .andExpect(status().is4xxClientError());
  }
}
