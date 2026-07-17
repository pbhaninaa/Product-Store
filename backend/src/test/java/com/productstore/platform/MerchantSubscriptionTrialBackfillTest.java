package com.productstore.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
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
class MerchantSubscriptionTrialBackfillTest {

  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired PasswordHasher passwordHasher;

  @BeforeEach
  void clean() {
    subscriptionRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  @Test
  void oldAccount_missingTrialDates_backfilledFromCreatedAt_noFreshTrial() throws Exception {
    Instant created = Instant.now().minus(60, ChronoUnit.DAYS);
    SeededMerchant m = seedMerchant("old-trial", "owner-old@test.local", created);

    MerchantSubscriptionEntity row = new MerchantSubscriptionEntity();
    row.tenantId = m.tenantId;
    row.planTier = TenantEntity.SubscriptionPlan.STARTER;
    row.active = false;
    row.trialUsed = false;
    row.onTrial = false;
    row.trialDatesBackfilled = false;
    subscriptionRepository.save(row);

    mvc.perform(
            get("/api/m/old-trial/admin/subscription/me")
                .header("Authorization", "Bearer " + m.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.onTrial").value(false))
        .andExpect(jsonPath("$.trialUsed").value(true))
        .andExpect(jsonPath("$.trialExpired").value(true))
        .andExpect(jsonPath("$.needsPayment").value(true));

    MerchantSubscriptionEntity after = subscriptionRepository.findByTenantId(m.tenantId).orElseThrow();
    assertTrue(after.trialDatesBackfilled);
    assertEquals(created.truncatedTo(ChronoUnit.MILLIS), after.trialStartAt.truncatedTo(ChronoUnit.MILLIS));
    assertEquals(
        created.plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS),
        after.trialEndAt.truncatedTo(ChronoUnit.MILLIS));
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(after, Instant.now()));
  }

  @Test
  void paidActiveSubscription_backfillDoesNotAlterPaidPeriod() throws Exception {
    Instant created = Instant.now().minus(90, ChronoUnit.DAYS);
    SeededMerchant m = seedMerchant("paid-keep", "owner-paid@test.local", created);

    LocalDate start = LocalDate.now(ZONE).minusDays(5);
    LocalDate end = LocalDate.now(ZONE).plusDays(20);
    MerchantSubscriptionEntity row = new MerchantSubscriptionEntity();
    row.tenantId = m.tenantId;
    row.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    row.billedPlanTier = TenantEntity.SubscriptionPlan.STANDARD;
    row.active = true;
    row.periodStart = start;
    row.periodEnd = end;
    row.trialUsed = true;
    row.onTrial = false;
    row.trialDatesBackfilled = false;
    row.paymentProofAutoSummary = "Paid online via Peach Payments.";
    subscriptionRepository.save(row);

    mvc.perform(
            get("/api/m/paid-keep/admin/subscription/me")
                .header("Authorization", "Bearer " + m.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.onTrial").value(false))
        .andExpect(jsonPath("$.planTier").value("STANDARD"))
        .andExpect(jsonPath("$.features.payroll").value(true))
        .andExpect(jsonPath("$.features.whatsapp").value(false));

    MerchantSubscriptionEntity after = subscriptionRepository.findByTenantId(m.tenantId).orElseThrow();
    assertTrue(after.trialDatesBackfilled);
    assertNotNull(after.trialStartAt);
    assertNotNull(after.trialEndAt);
    assertEquals(start, after.periodStart);
    assertEquals(end, after.periodEnd);
    assertEquals(TenantEntity.SubscriptionPlan.STANDARD, after.billedPlanTier);
    assertTrue(after.active);
    assertFalse(after.onTrial);
  }

  @Test
  void secondStatusFetch_doesNotReissueOrMoveTrialDates() throws Exception {
    Instant created = Instant.now().minus(10, ChronoUnit.DAYS);
    SeededMerchant m = seedMerchant("stable-trial", "owner-stable@test.local", created);
    subscriptions.provisionNewMerchantSubscription(m.tenantId);

    MerchantSubscriptionEntity first = subscriptionRepository.findByTenantId(m.tenantId).orElseThrow();
    Instant s1 = first.trialStartAt;
    Instant e1 = first.trialEndAt;

    mvc.perform(
            get("/api/m/stable-trial/admin/subscription/me")
                .header("Authorization", "Bearer " + m.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onTrial").value(true));

    // Simulate another ensure/backfill pass
    mvc.perform(
            get("/api/m/stable-trial/admin/subscription/me")
                .header("Authorization", "Bearer " + m.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onTrial").value(true));

    MerchantSubscriptionEntity second = subscriptionRepository.findByTenantId(m.tenantId).orElseThrow();
    assertEquals(s1, second.trialStartAt);
    assertEquals(e1, second.trialEndAt);
  }

  private SeededMerchant seedMerchant(String slug, String email, Instant createdAt) throws Exception {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = slug;
    t.modulesJson = "{}";
    t.subscriptionPlan = TenantEntity.SubscriptionPlan.STARTER;
    t.createdAt = createdAt;
    tenantRepository.save(t);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = email;
    owner.passwordHash = passwordHasher.hash("Owner@123456");
    owner.createdAt = Instant.now();
    userRepository.save(owner);

    MembershipEntity mem = new MembershipEntity();
    mem.id = UUID.randomUUID();
    mem.userId = owner.id;
    mem.tenantId = t.id;
    mem.role = Role.MERCHANT_OWNER;
    mem.createdAt = Instant.now();
    membershipRepository.save(mem);

    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Owner@123456\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    return new SeededMerchant(t.id, token);
  }

  private record SeededMerchant(UUID tenantId, String token) {}
}
