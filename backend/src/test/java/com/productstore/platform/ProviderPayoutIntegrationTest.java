package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SupportAuditLogEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.repositories.SupportAuditLogRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Provider payouts and the platform commission through the API (plan §4 endpoints 1-7), on H2. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderPayoutIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ApplicationContext ctx;
  @Autowired PlatformCommissionService commission;
  @Autowired ProviderPayoutService payoutService;
  @Autowired ProviderPayoutRepository payouts;
  @Autowired ProviderPayoutLineRepository payoutLines;
  @Autowired OnlinePaymentAttemptRepository attempts;
  @Autowired SupportAuditLogRepository auditLogs;

  PayoutFixtures fx;
  PayoutFixtures.User admin;

  @BeforeEach
  void setUp() {
    fx = new PayoutFixtures(ctx);
    admin = fx.user(Role.PLATFORM_ADMIN, null);
    commission.set("10", admin.principal());
  }

  @AfterEach
  void resetCommission() {
    commission.set("0", admin.principal());
  }

  // ------------------------------------------------------------------ commission (AC-3, AC-4)

  @Test
  void commissionIsStoredAuditedAndShown() throws Exception {
    List<UUID> before = auditIds("COMMISSION_UPDATE");
    as(admin, put("/api/support/platform-commission").content("{\"commissionPercent\":\"12.5\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissionBasisPoints").value(1250))
        .andExpect(jsonPath("$.commissionPercent").value("12.50"))
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());

    as(admin, get("/api/support/platform-commission"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissionBasisPoints").value(1250));
    SupportAuditLogEntity audit = onlyNewAudit("COMMISSION_UPDATE", before);
    assertThat(audit.detail).isEqualTo("from=1000bp;to=1250bp");
    assertThat(audit.actorUserId).isEqualTo(admin.id());
  }

  @Test
  void invalidCommissionIsRejectedAndTheStoredValueIsUnchanged() throws Exception {
    for (String body :
        List.of("{\"commissionPercent\":51}", "{\"commissionPercent\":-1}", "{\"commissionPercent\":\"abc\"}",
            "{\"commissionPercent\":\"12.345\"}", "{}")) {
      as(admin, put("/api/support/platform-commission").content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("commission_invalid"));
    }
    assertThat(commission.currentBasisPoints()).isEqualTo(1000);
  }

  /** AC-5: support staff, merchants and clients get 403; no token gets 401. */
  @Test
  void everyPayoutAndCommissionEndpointIsPlatformAdminOnly() throws Exception {
    TenantEntity m = fx.merchant("Authz Store");
    List<PayoutFixtures.User> refused =
        List.of(fx.user(Role.SUPPORT_USER, null), fx.user(Role.MERCHANT_OWNER, m.id), fx.user(Role.CLIENT, null));
    List<Supplier<MockHttpServletRequestBuilder>> calls =
        List.of(
            () -> get("/api/support/platform-commission"),
            () -> put("/api/support/platform-commission").content("{\"commissionPercent\":\"20\"}"),
            () -> get("/api/support/payouts/owed"),
            () -> get("/api/support/payouts/owed/" + m.id),
            () -> post("/api/support/payouts").content("{\"tenantId\":\"" + m.id + "\",\"reference\":\"X\"}"),
            () -> get("/api/support/payouts"),
            () -> get("/api/support/payouts/" + UUID.randomUUID()));
    for (Supplier<MockHttpServletRequestBuilder> call : calls) {
      mvc.perform(call.get().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
      for (PayoutFixtures.User u : refused) {
        as(u, call.get())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.commissionBasisPoints").doesNotExist())
            .andExpect(jsonPath("$.merchants").doesNotExist())
            .andExpect(jsonPath("$.payouts").doesNotExist());
      }
    }
    assertThat(commission.currentBasisPoints()).isEqualTo(1000);
  }

  // ------------------------------------------------------------------ owed (AC-7, AC-8)

  @Test
  void owedListShowsGrossFeesCommissionAndNetPerMerchant() throws Exception {
    TenantEntity m = fx.merchant("Owed Salon");
    OnlinePaymentAttemptEntity paid = fx.bookingPayment(m.id, 100_000, 2500L);

    JsonNode row = owedRow(m.id);

    assertThat(row.get("merchantName").asText()).isEqualTo("Owed Salon");
    assertThat(row.get("merchantSlug").asText()).isEqualTo(m.slug);
    assertThat(row.get("paymentCount").asInt()).isEqualTo(1);
    assertThat(row.get("grossCents").asLong()).isEqualTo(100_000);
    assertThat(row.get("knownFeeCents").asLong()).isEqualTo(2500);
    assertThat(row.get("unknownFeeCount").asInt()).isZero();
    assertThat(row.get("commissionCents").asLong()).isEqualTo(10_000);
    assertThat(row.get("netOwedCents").asLong()).isEqualTo(87_500);
    assertThat(Instant.parse(row.get("oldestPaidAt").asText())).isCloseTo(paid.outcomeAt, within(1, ChronoUnit.MILLIS));
  }

  @Test
  void nonPayablePaymentsAreNotCountedAndMerchantsWithNothingOwedAreNotListed() throws Exception {
    Instant at = Instant.now().minusSeconds(120);
    TenantEntity m = fx.merchant("Mixed Store");
    fx.bookingPayment(m.id, 10_000, 300L); // the only payable one
    fx.bookingPayment(m.id, 20_000, 300L, SalonBookingEntity.Status.confirmed, OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND, at);
    fx.bookingPayment(m.id, 30_000, 300L, SalonBookingEntity.Status.confirmed, OnlinePaymentAttemptEntity.Outcome.DUPLICATE, at);
    fx.bookingPayment(m.id, 40_000, null, SalonBookingEntity.Status.pending, OnlinePaymentAttemptEntity.Outcome.PENDING, at);
    fx.bookingPayment(m.id, 50_000, 300L, SalonBookingEntity.Status.cancelled, OnlinePaymentAttemptEntity.Outcome.PAID, at);
    fx.orderPayment(m.id, 60_000, 300L, OrderEntity.OrderStatus.cancelled, OnlinePaymentAttemptEntity.Outcome.PAID, at);
    fx.orderPayment(m.id, 70_000, 300L, OrderEntity.OrderStatus.paid, OnlinePaymentAttemptEntity.Outcome.NOT_PAID, at);
    TenantEntity nothing = fx.merchant("Nothing Owed");
    fx.orderPayment(nothing.id, 80_000, 300L, OrderEntity.OrderStatus.cancelled, OnlinePaymentAttemptEntity.Outcome.PAID, at);

    JsonNode owed = body(as(admin, get("/api/support/payouts/owed")).andExpect(status().isOk()));

    JsonNode row = find(owed.get("merchants"), "tenantId", m.id.toString());
    assertThat(row.get("paymentCount").asInt()).isEqualTo(1);
    assertThat(row.get("grossCents").asLong()).isEqualTo(10_000);
    assertThat(find(owed.get("merchants"), "tenantId", nothing.id.toString())).isNull();
    assertThat(owed.get("commissionPercent").asText()).isEqualTo("10.00");
    assertThat(owed.get("totals").get("paymentCount").asInt()).isGreaterThanOrEqualTo(1);
  }

  // ------------------------------------------------------------------ release screen (AC-9)

  @Test
  void releaseScreenShowsBankDetailsAndNamesTheMissingFields() throws Exception {
    TenantEntity m = fx.merchant("Half Banked", "", "Thandi Store", "", "250655");
    fx.bookingPayment(m.id, 10_000, null);

    as(admin, get("/api/support/payouts/owed/" + m.id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.merchant.slug").value(m.slug))
        .andExpect(jsonPath("$.bankDetails.accountHolder").value("Thandi Store"))
        .andExpect(jsonPath("$.bankDetails.branchCode").value("250655"))
        .andExpect(jsonPath("$.missingBankFields.length()").value(2))
        .andExpect(jsonPath("$.missingBankFields[0]").value("bankName"))
        .andExpect(jsonPath("$.missingBankFields[1]").value("accountNumber"))
        .andExpect(jsonPath("$.payments[0].feeCents").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.summary.unknownFeeCount").value(1))
        .andExpect(jsonPath("$.payableVersion").isNotEmpty());

    as(admin, get("/api/support/payouts/owed/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  // ------------------------------------------------------------------ release (AC-10..AC-15)

  @Test
  void releaseRecordsThePayoutLocksThePaymentsAndAudits() throws Exception {
    TenantEntity m = fx.merchant("Release Salon");
    OnlinePaymentAttemptEntity a = fx.bookingPayment(m.id, 100_000, 2500L);
    OnlinePaymentAttemptEntity b = fx.orderPayment(
        m.id, 50_000, null, OrderEntity.OrderStatus.paid, OnlinePaymentAttemptEntity.Outcome.PAID,
        Instant.now().minus(30, ChronoUnit.DAYS)); // older than any 7-day window: still owed and released
    JsonNode preview = preview(m.id);
    List<UUID> before = auditIds("PAYOUT_RELEASE");

    JsonNode out =
        body(
            as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview, null, " EFT-2026-11-23 ", "paid via FNB")))
                .andExpect(status().isOk()));

    JsonNode p = out.get("payout");
    assertThat(p.get("tenantId").asText()).isEqualTo(m.id.toString());
    assertThat(p.get("paymentCount").asInt()).isEqualTo(2);
    assertThat(p.get("grossCents").asLong()).isEqualTo(150_000);
    assertThat(p.get("capturedFeeCents").asLong()).isEqualTo(2500);
    assertThat(p.get("unknownFeeCount").asInt()).isEqualTo(1);
    assertThat(p.get("feeCents").asLong()).isEqualTo(2500);
    assertThat(p.get("feeAdjusted").asBoolean()).isFalse();
    assertThat(p.get("commissionBasisPoints").asInt()).isEqualTo(1000);
    assertThat(p.get("commissionPercent").asText()).isEqualTo("10.00");
    assertThat(p.get("commissionCents").asLong()).isEqualTo(15_000);
    assertThat(p.get("netCents").asLong()).isEqualTo(150_000 - 2500 - 15_000);
    assertThat(p.get("reference").asText()).isEqualTo("EFT-2026-11-23");
    assertThat(p.get("note").asText()).isEqualTo("paid via FNB");
    assertThat(p.get("releasedByEmail").asText()).isEqualTo(admin.email());
    assertThat(out.get("lines")).hasSize(2);
    assertThat(payoutLines.findReleasedAttemptIds(List.of(a.id, b.id))).containsExactlyInAnyOrder(a.id, b.id);

    // No longer owed.
    assertThat(owedRow(m.id)).isNull();
    as(admin, get("/api/support/payouts/owed/" + m.id)).andExpect(jsonPath("$.payments.length()").value(0));

    // AC-15: audited with the amounts and reference, never the bank account number.
    SupportAuditLogEntity entry = onlyNewAudit("PAYOUT_RELEASE", before);
    assertThat(entry.actorUserId).isEqualTo(admin.id());
    assertThat(entry.entityId).isEqualTo(p.get("id").asText());
    assertThat(entry.detail)
        .contains("merchant=" + m.slug, "tenantId=" + m.id, "netCents=" + p.get("netCents").asLong(),
            "reference=EFT-2026-11-23", "feeAdjusted=false")
        .doesNotContain("62000000001");

    // AC-3: a later commission change never alters a released payout.
    commission.set("20", admin.principal());
    as(admin, get("/api/support/payouts/" + p.get("id").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.commissionBasisPoints").value(1000))
        .andExpect(jsonPath("$.payout.commissionCents").value(15_000))
        .andExpect(jsonPath("$.lines.length()").value(2));
  }

  @Test
  void adjustedFeeTotalIsUsedForTheNetAndBothFeesAreStored() throws Exception {
    TenantEntity m = fx.merchant("Fee Adjust Salon");
    fx.bookingPayment(m.id, 100_000, null);
    JsonNode preview = preview(m.id);
    List<UUID> before = auditIds("PAYOUT_RELEASE");

    as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview, 3000L, "REF-30", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.feeCents").value(3000))
        .andExpect(jsonPath("$.payout.capturedFeeCents").value(0))
        .andExpect(jsonPath("$.payout.feeAdjusted").value(true))
        .andExpect(jsonPath("$.payout.netCents").value(100_000 - 3000 - 10_000));
    assertThat(onlyNewAudit("PAYOUT_RELEASE", before).detail).contains("feeAdjusted=true", "feeCents=3000");
  }

  @Test
  void invalidReleasesAreRejectedAndRecordNothing() throws Exception {
    TenantEntity m = fx.merchant("Invalid Release Salon");
    fx.bookingPayment(m.id, 10_000, 500L);
    JsonNode preview = preview(m.id);

    expectError(releaseBody(m.id, preview, null, "   ", null), 400, "payout_reference_required");
    expectError(releaseBody(m.id, preview, null, "R".repeat(101), null), 400, "payout_reference_too_long");
    expectError(releaseBody(m.id, preview, null, "REF", "n".repeat(501)), 400, "payout_note_too_long");
    expectError(releaseBody(m.id, preview, -1L, "REF", null), 400, "payout_fee_invalid");
    expectError(releaseBody(m.id, preview, null, "REF", null).replace("\"feeTotalCents\":null", "\"feeTotalCents\":12.5"), 400, "payout_fee_invalid");
    // R100 gross - R99 fee - R10 commission < 0
    expectError(releaseBody(m.id, preview, 9_900L, "REF", null), 400, "payout_net_not_positive");

    TenantEntity empty = fx.merchant("Empty Salon");
    JsonNode emptyPreview = preview(empty.id);
    expectError(releaseBody(empty.id, emptyPreview, null, "REF", null), 400, "payout_nothing_owed");

    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();
    assertThat(owedRow(m.id).get("paymentCount").asInt()).isEqualTo(1);
  }

  /** AC-13 / NFR-2: a repeated click or retried request finds the payments released and creates nothing. */
  @Test
  void repeatedReleaseIsRejectedAsAlreadyReleased() throws Exception {
    TenantEntity m = fx.merchant("Retry Salon");
    fx.bookingPayment(m.id, 10_000, 500L);
    String release = releaseBody(m.id, preview(m.id), null, "REF-1", null);

    as(admin, post("/api/support/payouts").content(release)).andExpect(status().isOk());
    expectError(release, 409, "payout_already_released");

    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).hasSize(1);
  }

  /** AC-13: two admins release the same merchant at the same moment: exactly one payout. */
  @Test
  void concurrentReleasesCreateExactlyOnePayout() throws Exception {
    TenantEntity m = fx.merchant("Race Salon");
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 5; i++) ids.add(fx.bookingPayment(m.id, 10_000 + i, 100L).id);
    PayoutDtos.ReleasePreview preview = payoutService.preview(m.id);
    PayoutFixtures.User secondAdmin = fx.user(Role.PLATFORM_ADMIN, null);

    List<Object> results = raceTwoReleases(m.id, preview, admin, secondAdmin);

    assertThat(results).filteredOn(r -> r instanceof PayoutDtos.PayoutDetail).hasSize(1);
    assertThat(results)
        .filteredOn(r -> r instanceof IllegalStateException e && "payout_already_released".equals(e.getMessage()))
        .hasSize(1);
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).hasSize(1);
    assertThat(payoutLines.findReleasedAttemptIds(ids)).hasSize(5).doesNotHaveDuplicates();
  }

  /** AC-14: a payment added, a payment no longer payable, or a commission change since the screen opened. */
  @Test
  void changedAmountsAreRejectedAndNothingIsRecorded() throws Exception {
    TenantEntity m = fx.merchant("Changed Salon");
    OnlinePaymentAttemptEntity first = fx.bookingPayment(m.id, 10_000, 500L);

    JsonNode before = preview(m.id);
    fx.bookingPayment(m.id, 20_000, 500L);
    expectError(releaseBody(m.id, before, null, "REF", null), 409, "payout_amounts_changed");

    JsonNode withBoth = preview(m.id);
    OnlinePaymentAttemptEntity flagged = attempts.findById(first.id).orElseThrow();
    flagged.outcome = OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND;
    attempts.save(flagged);
    expectError(releaseBody(m.id, withBoth, null, "REF", null), 409, "payout_amounts_changed");

    JsonNode atTenPercent = preview(m.id);
    commission.set("12", admin.principal());
    expectError(releaseBody(m.id, atTenPercent, null, "REF", null), 409, "payout_amounts_changed");

    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();
  }

  // ------------------------------------------------------------------ history (AC-16)

  @Test
  void historyFiltersByMerchantAndDatesAndOpensAPayout() throws Exception {
    TenantEntity m1 = fx.merchant("History One");
    TenantEntity m2 = fx.merchant("History Two");
    fx.bookingPayment(m1.id, 10_000, 100L);
    fx.bookingPayment(m2.id, 20_000, 200L);
    as(admin, post("/api/support/payouts").content(releaseBody(m1.id, preview(m1.id), null, "H1", "internal")))
        .andExpect(status().isOk());
    as(admin, post("/api/support/payouts").content(releaseBody(m2.id, preview(m2.id), null, "H2", null)))
        .andExpect(status().isOk());
    LocalDate today = LocalDate.now(ProviderEarningsService.ZONE);

    JsonNode page =
        body(
            as(admin, get("/api/support/payouts").param("tenantId", m1.id.toString())
                    .param("startDate", today.minusDays(6).toString()).param("endDate", today.toString()))
                .andExpect(status().isOk()));
    assertThat(page.get("total").asLong()).isEqualTo(1);
    assertThat(page.get("page").asInt()).isZero();
    assertThat(page.get("size").asInt()).isEqualTo(50);
    JsonNode p = page.get("payouts").get(0);
    assertThat(p.get("merchantName").asText()).isEqualTo("History One");
    assertThat(p.get("reference").asText()).isEqualTo("H1");
    assertThat(p.get("releasedByEmail").asText()).isEqualTo(admin.email());

    as(admin, get("/api/support/payouts").param("tenantId", m1.id.toString())
            .param("startDate", today.minusDays(20).toString()).param("endDate", today.minusDays(14).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    as(admin, get("/api/support/payouts").param("size", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(200));
    as(admin, get("/api/support/payouts").param("startDate", today.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("provide_both_start_and_end_or_neither"));

    as(admin, get("/api/support/payouts/" + p.get("id").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.note").value("internal"))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].grossCents").value(10_000))
        .andExpect(jsonPath("$.lines[0].feeCents").value(100))
        .andExpect(jsonPath("$.lines[0].kind").value("BOOKING"));
    as(admin, get("/api/support/payouts/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  // ------------------------------------------------------------------ helpers

  /** Runs two releases of the same screen at once; each result is the payout or the exception thrown. */
  private List<Object> raceTwoReleases(
      UUID tenantId, PayoutDtos.ReleasePreview preview, PayoutFixtures.User one, PayoutFixtures.User two)
      throws Exception {
    List<UUID> ids = preview.payments().stream().map(PayoutDtos.Line::attemptId).toList();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (PayoutFixtures.User u : List.of(one, two)) {
        Callable<Object> release =
            () -> {
              ready.countDown();
              go.await();
              try {
                return payoutService.release(
                    new ProviderPayoutService.ReleaseRequest(tenantId, ids, preview.payableVersion(), null, "RACE", null),
                    u.principal());
              } catch (RuntimeException e) {
                return e;
              }
            };
        futures.add(pool.submit(release));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<Object> out = new ArrayList<>();
      for (Future<Object> f : futures) out.add(f.get(30, TimeUnit.SECONDS));
      return out;
    } finally {
      pool.shutdownNow();
    }
  }

  private ResultActions as(PayoutFixtures.User u, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.header("Authorization", "Bearer " + u.token()).contentType(MediaType.APPLICATION_JSON));
  }

  private JsonNode body(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private JsonNode preview(UUID tenantId) throws Exception {
    return body(as(admin, get("/api/support/payouts/owed/" + tenantId)).andExpect(status().isOk()));
  }

  private JsonNode owedRow(UUID tenantId) throws Exception {
    JsonNode owed = body(as(admin, get("/api/support/payouts/owed")).andExpect(status().isOk()));
    return find(owed.get("merchants"), "tenantId", tenantId.toString());
  }

  private static JsonNode find(JsonNode array, String field, String value) {
    for (JsonNode n : array) {
      if (value.equals(n.path(field).asText())) return n;
    }
    return null;
  }

  private String releaseBody(UUID tenantId, JsonNode preview, Long fee, String reference, String note) throws Exception {
    List<String> ids = new ArrayList<>();
    preview.get("payments").forEach(p -> ids.add(p.get("attemptId").asText()));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", tenantId.toString());
    body.put("attemptIds", ids);
    body.put("payableVersion", preview.get("payableVersion").asText());
    body.put("feeTotalCents", fee);
    body.put("reference", reference);
    body.put("note", note);
    return json.writeValueAsString(body);
  }

  private void expectError(String releaseBody, int status, String code) throws Exception {
    as(admin, post("/api/support/payouts").content(releaseBody))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.error").value(code));
  }

  /** Ids of the audit rows for {@code action} that exist now, taken before the call under test. */
  private List<UUID> auditIds(String action) {
    return auditLogs.findAll().stream().filter(e -> action.equals(e.action)).map(e -> e.id).toList();
  }

  /**
   * The single audit row for {@code action} written since {@code before} was taken. Picking the newest
   * row by {@code createdAt} was flaky: rows written in the same clock tick tie.
   */
  private SupportAuditLogEntity onlyNewAudit(String action, List<UUID> before) {
    List<SupportAuditLogEntity> added = auditLogs.findAll().stream()
        .filter(e -> action.equals(e.action) && !before.contains(e.id))
        .toList();
    assertThat(added).hasSize(1);
    return added.get(0);
  }
}
