package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PlatformCommissionService;
import com.productstore.platform.services.ProviderEarningsService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The store owner's earnings and payouts (plan §4 endpoint 8): own store only, no admin-internal data. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreEarningsIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ApplicationContext ctx;
  @Autowired PlatformCommissionService commission;
  @Autowired ProviderPayoutService payoutService;
  @Autowired MerchantSubscriptionService subscriptions;

  PayoutFixtures fx;
  PayoutFixtures.User admin;
  TenantEntity store;
  PayoutFixtures.User owner;

  @BeforeEach
  void setUp() {
    fx = new PayoutFixtures(ctx);
    admin = fx.user(Role.PLATFORM_ADMIN, null);
    commission.set("10", admin.principal());
    store = fx.merchant("Owner Salon");
    subscriptions.forceActivatePlan(store.id, TenantEntity.SubscriptionPlan.STANDARD);
    owner = fx.user(Role.MERCHANT_OWNER, store.id);
  }

  @AfterEach
  void resetCommission() {
    commission.set("0", admin.principal());
  }

  /** AC-17 / AC-18: payments in the range, everything still owed, payouts in the range; no internal data. */
  @Test
  void ownerSeesOwnPaymentsWhatIsOwedAndPayoutsWithoutAdminData() throws Exception {
    Instant now = Instant.now();
    fx.bookingPayment(store.id, 40_000, 1000L); // released below
    payoutService.release(
        new ProviderPayoutService.ReleaseRequest(
            store.id, payoutService.preview(store.id).payments().stream().map(PayoutDtos.Line::attemptId).toList(),
            payoutService.preview(store.id).payableVersion(), null, "PAYREF-1", "admin only note"),
        admin.principal());
    fx.bookingPayment(store.id, 100_000, 2500L); // owed, in the range
    fx.bookingPayment(
        store.id, 50_000, null, SalonBookingEntity.Status.completed, OnlinePaymentAttemptEntity.Outcome.PAID,
        now.minus(40, ChronoUnit.DAYS)); // owed, older than the range
    fx.bookingPayment(
        store.id, 70_000, 700L, SalonBookingEntity.Status.cancelled, OnlinePaymentAttemptEntity.Outcome.PAID,
        now.minusSeconds(30)); // cancelled: neither owed nor paid out
    TenantEntity other = fx.merchant("Other Salon");
    fx.bookingPayment(other.id, 99_900, 100L);
    LocalDate today = LocalDate.now(ProviderEarningsService.ZONE);

    String raw =
        mvc.perform(
                get("/api/m/" + store.slug + "/admin/store-earnings")
                    .param("startDate", today.minusDays(6).toString())
                    .param("endDate", today.toString())
                    .header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode res = json.readTree(raw);

    assertThat(res.at("/range/startDate").asText()).isEqualTo(today.minusDays(6).toString());
    assertThat(res.at("/payments/count").asInt()).isEqualTo(2);
    assertThat(res.at("/payments/grossCents").asLong()).isEqualTo(140_000);
    assertThat(res.at("/payments/truncated").asBoolean()).isFalse();
    assertThat(res.at("/payments/items/0/status").asText()).isEqualTo("OWED");
    assertThat(res.at("/payments/items/0/grossCents").asLong()).isEqualTo(100_000);
    assertThat(res.at("/payments/items/1/status").asText()).isEqualTo("PAID_OUT");

    // Owed is not range limited: the 40 day old payment is included; same calculation as the Payouts page.
    assertThat(res.at("/owed/paymentCount").asInt()).isEqualTo(2);
    assertThat(res.at("/owed/grossCents").asLong()).isEqualTo(150_000);
    assertThat(res.at("/owed/knownFeeCents").asLong()).isEqualTo(2500);
    assertThat(res.at("/owed/unknownFeeCount").asInt()).isEqualTo(1);
    assertThat(res.at("/owed/commissionPercent").asText()).isEqualTo("10.00");
    assertThat(res.at("/owed/commissionCents").asLong()).isEqualTo(15_000);
    assertThat(res.at("/owed/netOwedCents").asLong()).isEqualTo(150_000 - 2500 - 15_000);

    assertThat(res.get("payouts")).hasSize(1);
    assertThat(res.at("/payouts/0/reference").asText()).isEqualTo("PAYREF-1");
    assertThat(res.at("/payouts/0/netCents").asLong()).isEqualTo(40_000 - 1000 - 4000);
    assertThat(res.at("/payouts/0/commissionPercent").asText()).isEqualTo("10.00");

    assertThat(raw)
        .doesNotContain("admin only note", "note", "releasedByEmail", admin.email(), "62000000001", "accountNumber",
            other.id.toString());
  }

  /** AC-18: staff of the store get permission_denied; another store's owner is not a member (forbidden). */
  @Test
  void staffAndOtherStoresOwnersAreRefused() throws Exception {
    PayoutFixtures.User staff = fx.user(Role.MERCHANT_STAFF, store.id);
    TenantEntity otherStore = fx.merchant("Neighbour Salon");
    PayoutFixtures.User otherOwner = fx.user(Role.MERCHANT_OWNER, otherStore.id);

    mvc.perform(get("/api/m/" + store.slug + "/admin/store-earnings").header("Authorization", "Bearer " + staff.token()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    mvc.perform(
            get("/api/m/" + store.slug + "/admin/store-earnings").header("Authorization", "Bearer " + otherOwner.token()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
    mvc.perform(get("/api/m/" + store.slug + "/admin/store-earnings")).andExpect(status().isUnauthorized());
  }

  @Test
  void datesAreBothOrNeitherAndNeitherMeansNoLimit() throws Exception {
    fx.bookingPayment(
        store.id, 50_000, 500L, SalonBookingEntity.Status.completed, OnlinePaymentAttemptEntity.Outcome.PAID,
        Instant.now().minus(400, ChronoUnit.DAYS));

    mvc.perform(
            get("/api/m/" + store.slug + "/admin/store-earnings")
                .param("startDate", "2026-11-17")
                .header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("provide_both_start_and_end_or_neither"));
    mvc.perform(get("/api/m/" + store.slug + "/admin/store-earnings").header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.range.startDate").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.payments.count").value(1))
        .andExpect(jsonPath("$.payments.items[0].feeCents").value(500))
        .andExpect(jsonPath("$.payouts").value(org.hamcrest.Matchers.empty()));
  }
}
