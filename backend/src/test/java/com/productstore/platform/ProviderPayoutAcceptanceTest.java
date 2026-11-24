package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.controllers.SupportPayoutController;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.ProviderPayoutEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.entities.SupportAuditLogEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.repositories.SupportAuditLogRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PaystackClient;
import com.productstore.platform.services.PlatformCommissionService;
import com.productstore.platform.services.ProviderEarningsService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Independent acceptance tests for provider payouts (QA task 12). Complements {@link
 * ProviderPayoutIntegrationTest} and {@link StoreEarningsIntegrationTest} with the role matrix gaps, money
 * boundaries, stale or forged release requests, SAST date boundaries and privacy of bank details. H2.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ProviderPayoutAcceptanceTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ApplicationContext ctx;
  @Autowired PlatformCommissionService commission;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired ProviderPayoutService payoutService;
  @Autowired ProviderPayoutRepository payouts;
  @Autowired ProviderPayoutLineRepository payoutLines;
  @Autowired OrderRepository orders;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired MembershipRepository memberships;
  @Autowired SubscriptionOnlinePaymentRepository subscriptionPayments;
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

  // ------------------------------------------------------------------ AC-5 / BR-8: role matrix

  /**
   * AC-5 gaps: store staff and a support user holding every support permission are refused on all 7 admin
   * endpoints; an invalid token is unauthenticated. A refused release with a fully valid body records nothing.
   */
  @Test
  void staffAndFullyPermittedSupportUsersAreRefusedOnEveryAdminEndpoint() throws Exception {
    TenantEntity m = fx.merchant("Matrix Store", "Nedbank", "Matrix Holder", "11122233344", "198765");
    fx.bookingPayment(m.id, 10_000, 300L);
    String validRelease = releaseBody(m.id, preview(m.id), null, "MATRIX-REF", "note");
    PayoutFixtures.User staff = fx.user(Role.MERCHANT_STAFF, m.id);
    PayoutFixtures.User support = fx.user(Role.SUPPORT_USER, null);
    for (MembershipEntity ms : memberships.findAllByUserId(support.id())) {
      ms.permissions = SupportPermission.toCsv(SupportPermission.ALL);
      memberships.save(ms);
    }
    List<Supplier<MockHttpServletRequestBuilder>> calls =
        List.of(
            () -> get("/api/support/platform-commission"),
            () -> put("/api/support/platform-commission").content("{\"commissionPercent\":\"20\"}"),
            () -> get("/api/support/payouts/owed"),
            () -> get("/api/support/payouts/owed/" + m.id),
            () -> post("/api/support/payouts").content(validRelease),
            () -> get("/api/support/payouts").param("tenantId", m.id.toString()),
            () -> get("/api/support/payouts/" + UUID.randomUUID()));

    for (Supplier<MockHttpServletRequestBuilder> call : calls) {
      for (PayoutFixtures.User u : List.of(staff, support)) {
        String body =
            as(u, call.get()).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(m.slug, "11122233344", "Matrix Holder", "commissionBasisPoints");
      }
      mvc.perform(
              call.get().header("Authorization", "Bearer not-a-real-token").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnauthorized());
    }

    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();
    assertThat(commission.currentBasisPoints()).isEqualTo(1000);
  }

  /** AC-5 / AC-18 on endpoint 8: only the store's own owner; platform admin, support and clients are refused. */
  @Test
  void storeEarningsIsRefusedToEveryoneButTheStoresOwner() throws Exception {
    TenantEntity store = fx.merchant("Owner Only Salon");
    subscriptions.forceActivatePlan(store.id, TenantEntity.SubscriptionPlan.STANDARD);
    fx.bookingPayment(store.id, 10_000, 300L);
    PayoutFixtures.User owner = fx.user(Role.MERCHANT_OWNER, store.id);
    String path = "/api/m/" + store.slug + "/admin/store-earnings";

    as(owner, get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.owed.paymentCount").value(1));
    for (Role role : List.of(Role.PLATFORM_ADMIN, Role.SUPPORT_USER, Role.CLIENT)) {
      PayoutFixtures.User u = fx.user(role, null);
      String body = as(u, get(path)).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
      assertThat(body).as(role.name()).doesNotContain("owed", "payments", "grossCents");
    }
  }

  // ------------------------------------------------------------------ BR-4 / AC-3 / AC-17: rounding and snapshot

  /**
   * BR-4: commission is rounded half-up per payment, not on the total (3 x R0.04 at 12.5% = 3 x 0.5c -> 3c,
   * where rounding the total would give 2c). AC-3: a % saved through the API applies to the next release and
   * later changes never alter it. AC-17: the owner sees the same owed figures as the Payouts page.
   */
  @Test
  void commissionIsRoundedHalfUpPerPaymentSnapshottedAndSameForOwnerAndAdmin() throws Exception {
    as(admin, put("/api/support/platform-commission").content("{\"commissionPercent\":\"12.5\"}"))
        .andExpect(status().isOk());
    TenantEntity m = fx.merchant("Rounding Salon");
    subscriptions.forceActivatePlan(m.id, TenantEntity.SubscriptionPlan.STANDARD);
    PayoutFixtures.User owner = fx.user(Role.MERCHANT_OWNER, m.id);
    for (int i = 0; i < 3; i++) fx.bookingPayment(m.id, 4, 0L); // 0.5 cent each -> 1 cent each
    fx.bookingPayment(m.id, 3, 0L); // 0.375 cent -> 0

    JsonNode row = owedRow(m.id);
    assertThat(row.get("grossCents").asLong()).isEqualTo(15);
    assertThat(row.get("commissionCents").asLong()).isEqualTo(3);
    assertThat(row.get("netOwedCents").asLong()).isEqualTo(12);
    JsonNode ownerOwed = body(as(owner, get("/api/m/" + m.slug + "/admin/store-earnings")).andExpect(status().isOk()))
        .get("owed");
    assertThat(ownerOwed.get("commissionPercent").asText()).isEqualTo("12.50");
    assertThat(ownerOwed.get("commissionCents").asLong()).isEqualTo(row.get("commissionCents").asLong());
    assertThat(ownerOwed.get("netOwedCents").asLong()).isEqualTo(row.get("netOwedCents").asLong());

    JsonNode released =
        body(as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview(m.id), null, "ROUND", null)))
            .andExpect(status().isOk()));
    String payoutId = released.at("/payout/id").asText();
    assertThat(released.at("/payout/commissionBasisPoints").asInt()).isEqualTo(1250);
    assertThat(released.at("/payout/commissionCents").asLong()).isEqualTo(3);
    assertThat(released.at("/payout/netCents").asLong()).isEqualTo(12);
    List<Long> lineCommission = new ArrayList<>();
    released.get("lines").forEach(l -> lineCommission.add(l.get("commissionCents").asLong()));
    assertThat(lineCommission).containsExactlyInAnyOrder(1L, 1L, 1L, 0L);

    as(admin, put("/api/support/platform-commission").content("{\"commissionPercent\":\"0\"}"))
        .andExpect(status().isOk());
    as(admin, get("/api/support/payouts/" + payoutId))
        .andExpect(jsonPath("$.payout.commissionBasisPoints").value(1250))
        .andExpect(jsonPath("$.payout.commissionPercent").value("12.50"))
        .andExpect(jsonPath("$.payout.commissionCents").value(3));
    as(owner, get("/api/m/" + m.slug + "/admin/store-earnings"))
        .andExpect(jsonPath("$.payouts[0].commissionPercent").value("12.50"))
        .andExpect(jsonPath("$.payouts[0].commissionCents").value(3));
  }

  // ------------------------------------------------------------------ AC-8: exclusions

  /** AC-8 / BR-1: a merchant whose only online payment is a platform subscription is owed nothing. */
  @Test
  void subscriptionPaymentsAreNeverOwed() throws Exception {
    TenantEntity m = fx.merchant("Subscriber Only");
    SubscriptionOnlinePaymentEntity sub = new SubscriptionOnlinePaymentEntity();
    sub.tenantId = m.id;
    sub.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    sub.amount = new BigDecimal("299.00");
    sub.status = SubscriptionOnlinePaymentEntity.STATUS_COMPLETED;
    sub.onlinePaymentChannel = OnlinePaymentChannel.CARD;
    sub.onlinePaymentReference = "SUBQA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    sub.completedAt = Instant.now();
    subscriptionPayments.save(sub);

    assertThat(owedRow(m.id)).isNull();
    as(admin, get("/api/support/payouts/owed/" + m.id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments.length()").value(0))
        .andExpect(jsonPath("$.summary.netOwedCents").value(0));
  }

  /** AC-8 + AC-14: an order cancelled after the screen opened blocks the stale release and is never owed. */
  @Test
  void orderCancelledAfterTheScreenOpenedIsRejectedAndThenLeftOut() throws Exception {
    TenantEntity m = fx.merchant("Cancel Later Store");
    OnlinePaymentAttemptEntity order =
        fx.orderPayment(m.id, 30_000, 900L, OrderEntity.OrderStatus.paid, OnlinePaymentAttemptEntity.Outcome.PAID,
            Instant.now().minusSeconds(300));
    OnlinePaymentAttemptEntity booking = fx.bookingPayment(m.id, 20_000, 600L);
    JsonNode stale = preview(m.id);

    OrderEntity o = orders.findById(order.recordId).orElseThrow();
    o.status = OrderEntity.OrderStatus.cancelled;
    orders.save(o);

    expectError(releaseBody(m.id, stale, null, "REF", null), 409, "payout_amounts_changed");
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();

    JsonNode fresh = preview(m.id);
    assertThat(fresh.get("payments")).hasSize(1);
    assertThat(fresh.at("/payments/0/attemptId").asText()).isEqualTo(booking.id.toString());
    as(admin, post("/api/support/payouts").content(releaseBody(m.id, fresh, null, "REF", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.paymentCount").value(1))
        .andExpect(jsonPath("$.payout.grossCents").value(20_000));
  }

  // ------------------------------------------------------------------ AC-14 / BR-6: forged or stale requests

  static Stream<Arguments> staleOrForgedReleases() {
    return Stream.of(
        Arguments.of("wrong version, right payments", "forged-version", "all"),
        Arguments.of("no version", "absent", "all"),
        Arguments.of("subset of the payments", "current", "first"),
        Arguments.of("a payment twice", "current", "duplicate"),
        Arguments.of("no payments", "current", "none"),
        Arguments.of("another merchant's payment and version", "other", "other"));
  }

  /** AC-14 / BR-6: the release must cover exactly the payments and version currently owed (A4). */
  @ParameterizedTest(name = "{0}")
  @MethodSource("staleOrForgedReleases")
  void releaseThatDoesNotMatchWhatIsOwedIsRejected(String label, String version, String ids) throws Exception {
    TenantEntity m = fx.merchant("Forged " + label);
    OnlinePaymentAttemptEntity a1 = fx.bookingPayment(m.id, 10_000, 100L);
    OnlinePaymentAttemptEntity a2 = fx.bookingPayment(m.id, 20_000, 200L);
    TenantEntity other = fx.merchant("Other " + label);
    OnlinePaymentAttemptEntity b = fx.bookingPayment(other.id, 30_000, 300L);
    String currentVersion = preview(m.id).get("payableVersion").asText();
    String otherVersion = preview(other.id).get("payableVersion").asText();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", m.id.toString());
    body.put(
        "attemptIds",
        switch (ids) {
          case "all" -> List.of(a1.id.toString(), a2.id.toString());
          case "first" -> List.of(a1.id.toString());
          case "duplicate" -> List.of(a1.id.toString(), a2.id.toString(), a2.id.toString());
          case "none" -> List.of();
          default -> List.of(b.id.toString());
        });
    switch (version) {
      case "forged-version" -> body.put("payableVersion", "0".repeat(64));
      case "current" -> body.put("payableVersion", currentVersion);
      case "other" -> body.put("payableVersion", otherVersion);
      default -> { /* absent */ }
    }
    body.put("reference", "FORGED");

    expectError(json.writeValueAsString(body), 409, "payout_amounts_changed");

    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(other.id)).isEmpty();
    assertThat(owedRow(m.id).get("paymentCount").asInt()).isEqualTo(2);
  }

  @Test
  void releaseForAnUnknownOrMissingMerchantIsNotFound() throws Exception {
    expectError(
        "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"attemptIds\":[],\"payableVersion\":\"x\",\"reference\":\"R\"}",
        404, "not_found");
    expectError("{\"attemptIds\":[],\"payableVersion\":\"x\",\"reference\":\"R\"}", 404, "not_found");
  }

  // ------------------------------------------------------------------ AC-10 / AC-11: boundaries

  /** AC-10: a net of exactly 0 is rejected, 1 cent is accepted; the fee must be a whole number of cents. */
  @Test
  void feeTotalBoundaries() throws Exception {
    TenantEntity m = fx.merchant("Fee Boundary Salon");
    fx.bookingPayment(m.id, 10_000, 500L); // commission 10% = 1 000
    JsonNode preview = preview(m.id);

    expectError(releaseBody(m.id, preview, 9_000L, "REF", null), 400, "payout_net_not_positive");
    expectError(
        releaseBody(m.id, preview, null, "REF", null).replace("\"feeTotalCents\":null", "\"feeTotalCents\":\"3000\""),
        400, "payout_fee_invalid");
    expectError(
        releaseBody(m.id, preview, null, "REF", null).replace("\"feeTotalCents\":null", "\"feeTotalCents\":3000.0"),
        400, "payout_fee_invalid");
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();

    as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview, 8_999L, "REF", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.netCents").value(1))
        .andExpect(jsonPath("$.payout.feeCents").value(8_999))
        .andExpect(jsonPath("$.payout.capturedFeeCents").value(500))
        .andExpect(jsonPath("$.payout.feeAdjusted").value(true));

    // A fee total equal to the captured fees is not an adjustment.
    TenantEntity same = fx.merchant("Fee Same Salon");
    fx.bookingPayment(same.id, 10_000, 500L);
    as(admin, post("/api/support/payouts").content(releaseBody(same.id, preview(same.id), 500L, "REF", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.feeAdjusted").value(false))
        .andExpect(jsonPath("$.payout.netCents").value(8_500));
  }

  /** AC-11: a missing or whitespace-only reference is rejected and nothing is recorded. */
  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "\r\n  \n"})
  void blankReferenceIsRejected(String reference) throws Exception {
    assertReferenceRequired(reference);
  }

  /**
   * AC-11 "only spaces": Unicode spaces (non-breaking, em, ideographic), e.g. pasted from a bank statement.
   * The UI rule (JavaScript trim) rejects these; the API must too, because authorization and validation are
   * server-side (NFR-3).
   */
  @ParameterizedTest
  @ValueSource(strings = {"  ", " ", "　"})
  void unicodeSpaceOnlyReferenceIsRejected(String reference) throws Exception {
    assertReferenceRequired(reference);
  }

  @Test
  void referenceOf100AndNoteOf500CharactersAreAccepted() throws Exception {
    TenantEntity m = fx.merchant("Long Text Salon");
    fx.bookingPayment(m.id, 10_000, 100L);
    String reference = "R".repeat(100);

    as(admin, post("/api/support/payouts")
            .content(releaseBody(m.id, preview(m.id), null, "  " + reference + "  ", "n".repeat(500))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payout.reference").value(reference))
        .andExpect(jsonPath("$.payout.note").value("n".repeat(500)));
  }

  // ------------------------------------------------------------------ AC-9: no shop settings at all

  /** AC-9: a merchant without any shop settings shows all four fields missing and can still be released. */
  @Test
  void merchantWithoutShopSettingsCanStillBeReleased() throws Exception {
    TenantEntity m = fx.merchant("No Settings Salon");
    shopSettings.findByTenantId(m.id).ifPresent(shopSettings::delete);
    fx.bookingPayment(m.id, 10_000, null);

    JsonNode preview = preview(m.id);
    assertThat(preview.get("missingBankFields").toString())
        .isEqualTo("[\"bankName\",\"accountHolder\",\"accountNumber\",\"branchCode\"]");

    JsonNode out =
        body(as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview, null, "NOBANK", null)))
            .andExpect(status().isOk()));
    // BR-5 / AC-6: an unknown fee stays unknown (null) on the line, never R0.
    assertThat(out.at("/payout/unknownFeeCount").asInt()).isEqualTo(1);
    assertThat(out.at("/lines/0/feeCents").isNull()).isTrue();
  }

  // ------------------------------------------------------------------ AC-15 / NFR-3: privacy

  /** NFR-3 / AC-15: bank details never reach the logs or the audit entry; the reference cannot forge fields. */
  @Test
  void bankDetailsNeverReachLogsOrAuditAndTheReferenceCannotForgeAuditFields(CapturedOutput output) throws Exception {
    TenantEntity m = fx.merchant("Private Bank Salon", "Capitec", "Nomsa Private-Holder", "98765432109", "470010");
    fx.bookingPayment(m.id, 10_000, 100L);
    int logStart = output.getAll().length();

    JsonNode preview = preview(m.id);
    assertThat(preview.at("/bankDetails/accountNumber").asText()).isEqualTo("98765432109");
    JsonNode out =
        body(as(admin, post("/api/support/payouts")
                .content(releaseBody(m.id, preview, null, "A;netCents=1\nX", "internal")))
            .andExpect(status().isOk()));

    String logs = output.getAll().substring(logStart);
    assertThat(logs).doesNotContain("98765432109", "Nomsa Private-Holder", "470010");
    SupportAuditLogEntity entry =
        auditLogs.findAll().stream()
            .filter(e -> "PAYOUT_RELEASE".equals(e.action) && out.at("/payout/id").asText().equals(e.entityId))
            .findFirst()
            .orElseThrow();
    assertThat(entry.detail).doesNotContain("98765432109", "Nomsa Private-Holder", "470010", "Capitec");
    assertThat(entry.detail.split(";netCents=", -1)).hasSize(2);
    assertThat(entry.detail).endsWith("reference=A,netCents=1,X");
  }

  // ------------------------------------------------------------------ AC-16 / AC-17 / A1: SAST date boundaries

  /** A1: payout history and the owner's payouts use South African calendar days on the release time. */
  @Test
  void historyRangesUseSouthAfricanCalendarDays() throws Exception {
    TenantEntity m = fx.merchant("SAST History Salon");
    subscriptions.forceActivatePlan(m.id, TenantEntity.SubscriptionPlan.STANDARD);
    PayoutFixtures.User owner = fx.user(Role.MERCHANT_OWNER, m.id);
    UUID first = releaseOne(m, Instant.parse("2026-11-16T22:00:00Z")); // 2026-11-17 00:00 SAST
    UUID second = releaseOne(m, Instant.parse("2026-11-16T21:59:59Z")); // 2026-11-16 23:59:59 SAST
    UUID third = releaseOne(m, Instant.parse("2026-11-23T22:00:00Z")); // 2026-11-24 00:00 SAST

    assertThat(historyIds(m.id, "2026-11-17", "2026-11-23")).containsExactly(first.toString());
    assertThat(historyIds(m.id, "2026-11-10", "2026-11-16")).containsExactly(second.toString());
    assertThat(historyIds(m.id, "2026-11-24", "2026-11-24")).containsExactly(third.toString());

    JsonNode ownerView =
        body(as(owner, get("/api/m/" + m.slug + "/admin/store-earnings")
                .param("startDate", "2026-11-17").param("endDate", "2026-11-23"))
            .andExpect(status().isOk()));
    assertThat(ownerView.get("payouts")).hasSize(1);
    assertThat(ownerView.at("/payouts/0/id").asText()).isEqualTo(first.toString());
  }

  /** A1 / AC-17: the owner's payments are placed in the range by their SAST settle date. */
  @Test
  void ownerPaymentsUseSouthAfricanCalendarDays() throws Exception {
    TenantEntity m = fx.merchant("SAST Owner Salon");
    subscriptions.forceActivatePlan(m.id, TenantEntity.SubscriptionPlan.STANDARD);
    PayoutFixtures.User owner = fx.user(Role.MERCHANT_OWNER, m.id);
    paidAt(m, 1_000, "2026-11-16T21:59:59Z"); // 16th 23:59:59 SAST: out
    paidAt(m, 2_000, "2026-11-16T22:00:00Z"); // 17th 00:00 SAST: in
    paidAt(m, 4_000, "2026-11-23T21:59:59Z"); // 23rd 23:59:59 SAST: in
    paidAt(m, 8_000, "2026-11-23T22:00:00Z"); // 24th 00:00 SAST: out

    as(owner, get("/api/m/" + m.slug + "/admin/store-earnings")
            .param("startDate", "2026-11-17").param("endDate", "2026-11-23"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments.count").value(2))
        .andExpect(jsonPath("$.payments.grossCents").value(6_000))
        // owed is never range limited
        .andExpect(jsonPath("$.owed.paymentCount").value(4))
        .andExpect(jsonPath("$.owed.grossCents").value(15_000));
  }

  // ------------------------------------------------------------------ AC-13 / BR-6: many clicks at once

  /** AC-13 beyond two callers: six admins (or clicks) at the same moment still give exactly one payout. */
  @Test
  void sixSimultaneousReleasesCreateExactlyOnePayout() throws Exception {
    TenantEntity m = fx.merchant("Six Way Race Salon");
    for (int i = 0; i < 3; i++) fx.bookingPayment(m.id, 10_000 + i, 100L);
    PayoutDtos.ReleasePreview preview = payoutService.preview(m.id);
    List<UUID> ids = preview.payments().stream().map(PayoutDtos.Line::attemptId).toList();
    int callers = 6;
    CountDownLatch ready = new CountDownLatch(callers);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    List<Object> results = new ArrayList<>();
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < callers; i++) {
        PayoutFixtures.User u = fx.user(Role.PLATFORM_ADMIN, null);
        Callable<Object> release =
            () -> {
              ready.countDown();
              go.await();
              try {
                return payoutService.release(
                    new ProviderPayoutService.ReleaseRequest(m.id, ids, preview.payableVersion(), null, "RACE6", null),
                    u.principal());
              } catch (RuntimeException e) {
                return e;
              }
            };
        futures.add(pool.submit(release));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      for (Future<Object> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    assertThat(results).filteredOn(r -> r instanceof PayoutDtos.PayoutDetail).hasSize(1);
    assertThat(results)
        .filteredOn(r -> !(r instanceof PayoutDtos.PayoutDetail))
        .allSatisfy(r -> assertThat(r)
            .isInstanceOf(IllegalStateException.class)
            .hasFieldOrPropertyWithValue("message", "payout_already_released"));
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).hasSize(1);
    assertThat(payoutLines.findReleasedAttemptIds(ids)).hasSize(3).doesNotHaveDuplicates();
  }

  // ------------------------------------------------------------------ BR-7: no money movement

  /** BR-7 / AC-12: nothing on the payout path can reach the Paystack client, so no transfer can be attempted. */
  @Test
  void payoutCodeHasNoPaystackDependency() {
    for (Class<?> type :
        List.of(SupportPayoutController.class, ProviderPayoutService.class, ProviderEarningsService.class,
            PlatformCommissionService.class)) {
      for (Constructor<?> c : type.getDeclaredConstructors()) {
        assertThat(Arrays.stream(c.getParameterTypes()).filter(PaystackClient.class::isAssignableFrom))
            .as(type.getSimpleName())
            .isEmpty();
      }
    }
  }

  // ------------------------------------------------------------------ helpers

  private void assertReferenceRequired(String reference) throws Exception {
    TenantEntity m = fx.merchant("Blank Ref Salon");
    fx.bookingPayment(m.id, 10_000, 100L);
    expectError(releaseBody(m.id, preview(m.id), null, reference, null), 400, "payout_reference_required");
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).isEmpty();
  }

  private void paidAt(TenantEntity m, long grossCents, String instant) {
    fx.bookingPayment(
        m.id, grossCents, 0L, SalonBookingEntity.Status.confirmed, OnlinePaymentAttemptEntity.Outcome.PAID,
        Instant.parse(instant));
  }

  /** Releases one new payment for {@code m} and moves the payout's release time to {@code releasedAt}. */
  private UUID releaseOne(TenantEntity m, Instant releasedAt) throws Exception {
    fx.bookingPayment(m.id, 10_000, 100L);
    JsonNode out =
        body(as(admin, post("/api/support/payouts").content(releaseBody(m.id, preview(m.id), null, "SAST", null)))
            .andExpect(status().isOk()));
    UUID id = UUID.fromString(out.at("/payout/id").asText());
    ProviderPayoutEntity p = payouts.findById(id).orElseThrow();
    p.releasedAt = releasedAt;
    payouts.save(p);
    return id;
  }

  private List<String> historyIds(UUID tenantId, String start, String end) throws Exception {
    JsonNode page =
        body(as(admin, get("/api/support/payouts").param("tenantId", tenantId.toString())
                .param("startDate", start).param("endDate", end))
            .andExpect(status().isOk()));
    List<String> ids = new ArrayList<>();
    page.get("payouts").forEach(p -> ids.add(p.get("id").asText()));
    return ids;
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
    for (JsonNode n : owed.get("merchants")) {
      if (tenantId.toString().equals(n.path("tenantId").asText())) return n;
    }
    return null;
  }

  private String releaseBody(UUID tenantId, JsonNode preview, Long fee, String reference, String note)
      throws Exception {
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
}
