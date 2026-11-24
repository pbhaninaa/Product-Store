package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.BookingFeeEventEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentRefundEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.BookingFeeEventRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OnlinePaymentRefundRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.services.BookingFeeRefundService;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.PaystackClient;
import com.productstore.platform.services.ProviderEarningsService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.ShopSettingsDefaults;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Booking fee for pay-in-store bookings (requirements/booking-fee.md) through the real HTTP API: every acceptance
 * criterion and edge case. Paystack is a test double; webhooks are signed as Paystack signs them; time is a clock
 * the test moves, so the 24 hour and 2 hour boundaries are exact.
 */
@SpringBootTest(
    classes = {PlatformApplication.class, BookingFeeIntegrationTest.ClockConfig.class},
    properties = {
      "paystack.enabled=true",
      "paystack.secret-key=" + BookingFeeIntegrationTest.SECRET,
      "app.frontend-base-url=http://localhost:8085",
      "paystack.verify-cache-ttl=PT0S"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFeeIntegrationTest {
  static final String SECRET = "sk_test_booking_fee";
  private static final String PASSWORD = "Secret@123456";
  private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

  /** A clock the test sets; starts at the real time so trials, tokens and the rest behave normally. */
  static final class MutableClock extends Clock {
    private volatile Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class ClockConfig {
    @Bean
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired SalonServiceRepository salonServices;
  @Autowired SalonStaffRepository salonStaff;
  @Autowired SalonStaffAvailabilityRepository salonAvailability;
  @Autowired SalonBookingRepository bookings;
  @Autowired OnlinePaymentAttemptRepository attempts;
  @Autowired OnlinePaymentRefundRepository refunds;
  @Autowired BookingFeeEventRepository feeEvents;
  @Autowired SalonBookingService salonBookingService;
  @Autowired BookingFeeRefundService refundService;
  @Autowired ProviderEarningsService earnings;
  @Autowired ProviderPayoutService payouts;
  @Autowired MerchantSubscriptionService subscriptions;

  @MockitoBean PaystackClient paystack;

  private final List<PaystackClient.InitializeRequest> initialized = new CopyOnWriteArrayList<>();
  private final List<PaystackClient.RefundRequest> refundRequests = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    initialized.clear();
    refundRequests.clear();
    when(paystack.initialize(any()))
        .thenAnswer(
            inv -> {
              PaystackClient.InitializeRequest r = inv.getArgument(0);
              initialized.add(r);
              return new PaystackClient.InitializeResult("https://checkout.paystack.com/" + r.reference(), r.reference());
            });
    when(paystack.verify(any())).thenReturn(Optional.empty());
    when(paystack.refund(any()))
        .thenAnswer(
            inv -> {
              refundRequests.add(inv.getArgument(0));
              return new PaystackClient.RefundResult("rf_" + refundRequests.size(), "pending", false);
            });
    String body = json.writeValueAsString(Map.of("email", uniqueEmail("sysadmin"), "password", PASSWORD));
    int code =
        mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(code).isIn(201, 409);
  }

  // ================================================================ US-1 provider sets a booking fee

  /** US-1 AC-1: a store owner saves 20%, and new cash bookings charge 20% online. */
  @Test
  void ownerSavesTwentyPercentAndNewCashBookingsChargeItOnline() throws Exception {
    Merchant m = registerMerchant();
    salon(m, "1000.00");
    Map<String, Object> saved = read(setFee(m.token, m, 20).andExpect(status().isOk()).andReturn());
    assertThat(saved.get("bookingFeePercent")).isEqualTo(20);

    Map<String, Object> publicSettings =
        read(mvc.perform(get("/api/public/m/" + m.slug + "/shop-settings")).andExpect(status().isOk()).andReturn());
    assertThat(publicSettings.get("bookingFeePercent")).isEqualTo(20);
    assertThat(publicSettings.get("bookingFeeActive")).isEqualTo(true);
    assertThat(publicSettings.get("bookingFeeMinimumZar")).isEqualTo("1.00");
    Map<String, Object> adminSettings =
        read(mvc.perform(auth(get("/api/m/" + m.slug + "/admin/store/settings"), m.token)).andExpect(status().isOk()).andReturn());
    assertThat(adminSettings.get("bookingFeePercent")).isEqualTo(20);

    // US-2 AC-1: R1 000 at 20% asks for R200 online and shows R800 due at the shop.
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    @SuppressWarnings("unchecked")
    Map<String, Object> fee = (Map<String, Object>) created.get("bookingFee");
    assertThat(fee.get("feeZar")).isEqualTo("200.00");
    assertThat(fee.get("remainderZar")).isEqualTo("800.00");
    assertThat(fee.get("totalZar")).isEqualTo("1000.00");
    assertThat(fee.get("percent")).isEqualTo(20);
    assertThat(fee.get("status")).isEqualTo("awaiting_payment");
    assertThat(fee.get("refundableIfCancelledNow")).isEqualTo(true);
    assertThat(created.get("needsOnlinePayment")).isEqualTo(true);
    assertThat(created.get("paymentMethod")).isEqualTo("cash_store");
    PaystackClient.InitializeRequest init = lastInitialize();
    assertThat(init.amountCents()).isEqualTo(20_000);
    assertThat(init.reference()).matches("BKG[A-Z0-9]{16}");
    assertThat(init.channels()).containsExactly("card");
    assertThat(init.metadata()).containsEntry("kind", "booking").containsEntry("id", (String) created.get("bookingId"));
    assertThat(created.get("paymentRedirectUrl")).isEqualTo("https://checkout.paystack.com/" + init.reference());
  }

  /** US-1 AC-2: 60%, a negative value or a fraction is refused with the allowed range; nothing changes. */
  @Test
  void feeOutsideZeroToFiftyIsRejectedWithTheAllowedRange() throws Exception {
    Merchant m = registerMerchant();
    setFee(m.token, m, 10).andExpect(status().isOk());
    for (String bad : List.of("60", "-1", "50.5", "12.5", "null")) {
      Map<String, Object> out =
          read(
              mvc.perform(
                      auth(put("/api/m/" + m.slug + "/admin/store/booking-fee"), m.token)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"bookingFeePercent\":" + bad + "}"))
                  .andExpect(status().isBadRequest())
                  .andReturn());
      assertThat(out.get("error")).isEqualTo("booking_fee_percent_out_of_range");
    }
    assertThat(shopSettings.findByTenantId(m.tenantId).orElseThrow().bookingFeePercent).isEqualTo(10);
    // The limits themselves are allowed.
    setFee(m.token, m, 0).andExpect(status().isOk());
    setFee(m.token, m, 50).andExpect(status().isOk());
  }

  /** US-1 AC-3: staff without the store settings permission, another store's owner and anonymous callers get 403. */
  @Test
  void usersWithoutPermissionToChangeSettingsAreRefused() throws Exception {
    Merchant m = registerMerchant();
    Merchant other = registerMerchant();
    String staffEmail = uniqueEmail("staff");
    mvc.perform(
            auth(post("/api/m/" + m.slug + "/admin/team"), m.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email", staffEmail, "password", PASSWORD, "displayName", "Staff",
                            "payMethod", "PER_SERVICE", "payRate", 100))))
        .andExpect(status().isOk());
    String staffToken = login(staffEmail);

    setFee(staffToken, m, 20).andExpect(status().isForbidden());
    setFee(other.token, m, 20).andExpect(status().isForbidden());
    mvc.perform(
            put("/api/m/" + m.slug + "/admin/store/booking-fee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingFeePercent\":20}"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    assertThat(shopSettings.findByTenantId(m.tenantId).map(s -> s.bookingFeePercent).orElse(null)).isNull();
  }

  // ================================================================ US-2 customer pays the fee

  /** Fee paid by webhook: booking confirmed with the remainder due; a replayed webhook never double-confirms (NFR-4). */
  @Test
  void paidFeeConfirmsTheBookingOnceEvenWhenTheWebhookIsReplayed() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    String email = uniqueEmail("c");
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", email, "EFT");
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    PaystackClient.InitializeRequest init = lastInitialize();
    assertThat(init.channels()).containsExactly("eft");

    byte[] body = chargeSuccess(init.reference(), 20_000, bookingId);
    sendWebhook(body).andExpect(status().isOk());
    sendWebhook(body).andExpect(status().isOk());
    sendWebhook(body).andExpect(status().isOk());

    SalonBookingEntity b = bookings.findById(bookingId).orElseThrow();
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.paid);
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
    assertThat(b.amountZar).isEqualByComparingTo("1000.00");
    assertThat(attempts.findByReference(init.reference()).orElseThrow().outcome)
        .isEqualTo(OnlinePaymentAttemptEntity.Outcome.PAID);
    assertThat(events(bookingId, BookingFeeEventEntity.Type.FEE_PAID)).hasSize(1);

    Map<String, Object> statusPage = paymentStatus(m, bookingId, email);
    assertThat(statusPage.get("paid")).isEqualTo(true);
    assertThat(statusPage.get("paymentPurpose")).isEqualTo("booking_fee");
    assertThat(statusPage.get("amountDueAtShopZar")).isEqualTo("800.00");
    assertThat(statusPage.get("handoverCode")).isEqualTo(created.get("handoverCode"));
    assertThat(lineKinds(statusPage)).containsExactly("booking_fee_online", "due_at_shop", "total");

  }

  /** US-2 AC-2 / FR-5 / BR-3: an unpaid fee cancels the booking after 2 hours and frees the slot. */
  @Test
  void unpaidFeeCancelsTheBookingAfterTwoHoursAndFreesTheSlot() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Instant start = startIn(Duration.ofDays(3));
    Map<String, Object> created = book(m, start, "cash_store", uniqueEmail("c"), null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    Instant bookedAt = clock.instant();

    // The only stylist's slot is taken while the fee is awaited.
    expectBookingError(m, start, "cash_store", "slot_full");

    clock.set(bookedAt.plus(Duration.ofMinutes(119)));
    salonBookingService.expireUnpaidBookingFees(clock.instant(), Duration.ofHours(2));
    assertThat(bookings.findById(bookingId).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.pending);

    clock.set(bookedAt.plus(Duration.ofHours(2)).plusSeconds(1));
    salonBookingService.expireUnpaidBookingFees(clock.instant(), Duration.ofHours(2));
    SalonBookingEntity b = bookings.findById(bookingId).orElseThrow();
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.not_paid);
    assertThat(b.cancelledAt).isEqualTo(clock.instant());

    // The slot is free again.
    Map<String, Object> again = book(m, start, "cash_store", uniqueEmail("c2"), null);
    assertThat(again.get("bookingId")).isNotNull();
  }

  /** US-2 AC-3 / FR-3: with a 0% fee a cash booking behaves as before, with no online step. */
  @Test
  void zeroPercentFeeKeepsTheCashBookingFlowUnchanged() throws Exception {
    Merchant m = feeSalon("1000.00", 0);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    assertThat(created).doesNotContainKeys("bookingFee", "needsOnlinePayment", "paymentRedirectUrl");
    assertThat(created.get("needsCashPaymentCode")).isEqualTo(true);
    assertThat(initialized).isEmpty();
    SalonBookingEntity b = bookings.findById(UUID.fromString((String) created.get("bookingId"))).orElseThrow();
    assertThat(b.bookingFeeZar).isNull();
    assertThat(b.bookingFeeStatus).isNull();
    assertThat(b.onlinePaymentChannel).isNull();
    // A channel is still not applicable to a cash booking without a fee, as before.
    Map<String, Object> req = bookingRequest(startIn(Duration.ofDays(4)), "cash_store", uniqueEmail("x"), "CARD");
    req.put("serviceId", serviceOf(m).toString());
    Map<String, Object> err =
        read(mvc.perform(post("/api/public/m/" + m.slug + "/salon/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isBadRequest()).andReturn());
    assertThat(err.get("error")).isEqualTo("online_payment_channel_not_applicable");
    // Staff confirm the full amount with the code.
    Map<String, Object> row = adminRow(m, b.id);
    assertThat(row.get("amountDueZar")).isEqualTo("1000.00");
    assertThat(row.get("bookingFeeStatus")).isEqualTo("");
  }

  /** US-2 AC-4 / FR-4: in-app bookings pay the full R1 000 and show no booking fee. */
  @Test
  void inAppBookingPaysTheFullAmountWithoutABookingFee() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "online", uniqueEmail("c"), "CARD");
    assertThat(created).doesNotContainKey("bookingFee");
    assertThat(lastInitialize().amountCents()).isEqualTo(100_000);
    SalonBookingEntity b = bookings.findById(UUID.fromString((String) created.get("bookingId"))).orElseThrow();
    assertThat(b.bookingFeeZar).isNull();
    assertThat(b.hasBookingFee()).isFalse();

    sendWebhook(chargeSuccess(lastInitialize().reference(), 100_000, b.id)).andExpect(status().isOk());
    assertThat(bookings.findById(b.id).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.confirmed);
  }

  /** Edge case 6 / open question 1: a fee below Paystack's minimum charge (R1.00) skips the online step. */
  @Test
  void feeBelowThePaystackMinimumSkipsTheOnlineStep() throws Exception {
    Merchant m = feeSalon("4.00", 20); // 20% of R4 = R0.80
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    assertThat(created).doesNotContainKeys("bookingFee", "needsOnlinePayment");
    assertThat(initialized).isEmpty();
  }

  /** Edge case 3 / BR-7: changing the percent later does not change an existing booking's fee. */
  @Test
  void laterPercentChangeKeepsTheOriginalFee() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    setFee(m.token, m, 50).andExpect(status().isOk());
    SalonServiceEntity svc = salonServices.findById(bookings.findById(bookingId).orElseThrow().serviceId).orElseThrow();
    svc.priceZar = new BigDecimal("2000.00");
    salonServices.save(svc);

    sendWebhook(chargeSuccess(lastInitialize().reference(), 20_000, bookingId)).andExpect(status().isOk());
    SalonBookingEntity b = bookings.findById(bookingId).orElseThrow();
    assertThat(b.bookingFeePercent).isEqualTo(20);
    assertThat(b.bookingFeeZar).isEqualByComparingTo("200.00");
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.paid);
    assertThat(adminRow(m, bookingId).get("amountDueZar")).isEqualTo("800.00");
  }

  /** Edge case 1: booking less than 24 hours ahead is allowed and tells the customer the fee is not refundable. */
  @Test
  void bookingLessThanADayAheadIsAllowedAndShownAsNotRefundable() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Instant start = startIn(Duration.ofDays(3));
    clock.set(start.minus(Duration.ofHours(5)));
    Map<String, Object> created = book(m, start, "cash_store", uniqueEmail("c"), null);
    @SuppressWarnings("unchecked")
    Map<String, Object> fee = (Map<String, Object>) created.get("bookingFee");
    assertThat(fee.get("refundableIfCancelledNow")).isEqualTo(false);
  }

  // ================================================================ US-3 staff collect the remainder

  /** US-3 AC-1/AC-2 / FR-6: staff see R800 due; the code confirms only the remainder and marks it fully paid. */
  @Test
  void staffSeeTheRemainderAndTheCodeMarksTheBookingFullyPaid() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    String code = (String) created.get("handoverCode");

    // Fee not paid yet: staff cannot take the cash.
    Map<String, Object> unpaid = adminRow(m, bookingId);
    assertThat(unpaid.get("bookingFeeStatus")).isEqualTo("awaiting_payment");
    Map<String, Object> refused =
        read(confirmCash(m, bookingId, code).andExpect(status().isBadRequest()).andReturn());
    assertThat(refused.get("error")).isEqualTo("booking_fee_unpaid");

    sendWebhook(chargeSuccess(lastInitialize().reference(), 20_000, bookingId)).andExpect(status().isOk());
    Map<String, Object> row = adminRow(m, bookingId);
    assertThat(row.get("amountDueZar")).isEqualTo("800.00");
    assertThat(row.get("bookingFeeZar")).isEqualTo("200.00");
    assertThat(row.get("bookingFeeStatus")).isEqualTo("paid");
    assertThat(row.get("paid")).isEqualTo(false);
    assertThat(row.get("cashPaymentCode")).isEqualTo(""); // staff never see the code; the customer shows it

    Map<String, Object> wrong = read(confirmCash(m, bookingId, "000000").andExpect(status().isOk()).andReturn());
    assertThat(wrong.get("ok")).isEqualTo(false);
    Map<String, Object> ok = read(confirmCash(m, bookingId, code).andExpect(status().isOk()).andReturn());
    assertThat(ok.get("ok")).isEqualTo(true);

    Map<String, Object> paid = adminRow(m, bookingId);
    assertThat(paid.get("status")).isEqualTo("confirmed");
    assertThat(paid.get("paid")).isEqualTo(true);
    assertThat(paid.get("amountDueZar")).isEqualTo("0.00");
    assertThat(lineKinds(paid)).containsExactly("booking_fee_online", "paid_at_shop", "total");
    assertThat(lineAmounts(paid)).containsExactly("200.00", "800.00", "1000.00");
  }

  /** Starting the service with the code from pending also collects only the remainder (handover code integration). */
  @Test
  void startingTheServiceWithTheCodeCollectsTheRemainder() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    sendWebhook(chargeSuccess(lastInitialize().reference(), 20_000, bookingId)).andExpect(status().isOk());
    mvc.perform(
            auth(post("/api/m/" + m.slug + "/admin/salon/bookings/" + bookingId + "/status"), m.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "in_progress", "code", created.get("handoverCode")))))
        .andExpect(status().isOk());
    Map<String, Object> row = adminRow(m, bookingId);
    assertThat(row.get("status")).isEqualTo("in_progress");
    assertThat(row.get("paid")).isEqualTo(true);
    assertThat(lineKinds(row)).containsExactly("booking_fee_online", "paid_at_shop", "total");
  }

  // ================================================================ US-4 cancellations and refunds

  /** US-4 AC-1 / BR-4: cancelling 25 hours before the start refunds the R200 fee through Paystack. */
  @Test
  void customerCancellingTwentyFiveHoursAheadGetsTheFeeRefunded() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofHours(25)));
    cancelAsCustomer(p).andExpect(status().isOk());
    Map<String, Object> out = read(lastCancel);
    assertThat(out.get("status")).isEqualTo("cancelled");
    assertThat(out.get("bookingFeeOutcome")).isEqualTo("refunded");
    assertThat(lineKinds(out)).containsExactly("booking_fee_online", "fee_refunded", "total");

    assertThat(refundRequests).hasSize(1);
    PaystackClient.RefundRequest r = refundRequests.get(0);
    assertThat(r.transactionReference()).isEqualTo(p.reference);
    assertThat(r.amountCents()).isEqualTo(20_000);
    assertThat(r.currency()).isEqualTo("ZAR");
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.customer_cancellation);
    assertThat(owedAttemptIds(p.tenantId)).doesNotContain(attemptId(p));
  }

  /** Edge case 2: cancelling exactly 24 hours before the start is "at least 24 hours": refunded. */
  @Test
  void cancellingExactlyTwentyFourHoursAheadIsRefunded() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofHours(24)));
    cancelAsCustomer(p).andExpect(status().isOk());
    assertThat(read(lastCancel).get("bookingFeeOutcome")).isEqualTo("refunded");
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.customer_cancellation);
  }

  /** US-4 AC-2 / BR-5: cancelling 23 hours before keeps the fee for the provider, recorded and payable. */
  @Test
  void customerCancellingTwentyThreeHoursAheadForfeitsTheFeeToTheProvider() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofHours(24)).plusSeconds(1));
    cancelAsCustomer(p).andExpect(status().isOk());
    Map<String, Object> out = read(lastCancel);
    assertThat(out.get("bookingFeeOutcome")).isEqualTo("kept");
    assertThat(lineKinds(out)).containsExactly("booking_fee_online", "fee_kept", "total");
    assertThat(refundRequests).isEmpty();
    verify(paystack, never()).refund(any());
    SalonBookingEntity b = bookings.findById(p.bookingId).orElseThrow();
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.kept);
    assertThat(b.bookingFeeReason).isEqualTo(SalonBookingEntity.BookingFeeReason.late_cancellation);
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.FEE_KEPT)).singleElement()
        .satisfies(e -> {
          assertThat(e.amountCents).isEqualTo(20_000);
          assertThat(e.reason).isEqualTo(SalonBookingEntity.BookingFeeReason.late_cancellation);
          assertThat(e.createdAt).isEqualTo(clock.instant());
        });
    // The kept fee is in-app money owed to the provider even though the booking is cancelled.
    assertThat(owedAttemptIds(p.tenantId)).contains(attemptId(p));
    // The slot is free again.
    assertThat(bookings.findById(p.bookingId).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.cancelled);
  }

  /** US-4 AC-3 / BR-5: the provider marks a no-show after the start; the fee is kept. Not before the start. */
  @Test
  void noShowKeepsTheFeeAndCannotBeMarkedBeforeTheStart() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofMinutes(1)));
    Map<String, Object> early =
        read(noShow(p.merchant, p.bookingId).andExpect(status().isBadRequest()).andReturn());
    assertThat(early.get("error")).isEqualTo("no_show_too_early");

    clock.set(p.start.plus(Duration.ofMinutes(20)));
    noShow(p.merchant, p.bookingId).andExpect(status().isOk());
    SalonBookingEntity b = bookings.findById(p.bookingId).orElseThrow();
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
    assertThat(b.noShowAt).isEqualTo(clock.instant());
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.kept);
    assertThat(b.bookingFeeReason).isEqualTo(SalonBookingEntity.BookingFeeReason.no_show);
    verify(paystack, never()).refund(any());
    assertThat(owedAttemptIds(p.tenantId)).contains(attemptId(p));
    Map<String, Object> row = adminRow(p.merchant, p.bookingId);
    assertThat(row.get("noShow")).isEqualTo(true);
    assertThat(lineKinds(row)).containsExactly("booking_fee_online", "fee_kept", "total");

    // A second no-show (or any change) on the cancelled booking is refused.
    noShow(p.merchant, p.bookingId).andExpect(status().isBadRequest());
  }

  /** No-show needs the bookings and cancel permissions; another store's booking is not found. */
  @Test
  void noShowIsRefusedForAnotherStore() throws Exception {
    Paid p = paidFeeBooking();
    Merchant other = registerMerchant();
    salon(other, "100.00");
    clock.set(p.start.plus(Duration.ofMinutes(20)));
    mvc.perform(auth(post("/api/m/" + p.merchant.slug + "/admin/salon/bookings/" + p.bookingId + "/no-show"), other.token))
        .andExpect(status().isForbidden());
    mvc.perform(auth(post("/api/m/" + other.slug + "/admin/salon/bookings/" + p.bookingId + "/no-show"), other.token))
        .andExpect(status().isNotFound());
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.paid);
  }

  /** US-4 AC-4 / BR-6: the provider cancelling 1 hour before the start refunds the fee. */
  @Test
  void providerCancellingAnHourBeforeRefundsTheFee() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofHours(1)));
    mvc.perform(
            auth(post("/api/m/" + p.merchant.slug + "/admin/salon/bookings/" + p.bookingId + "/status"), p.merchant.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"cancelled\"}"))
        .andExpect(status().isOk());
    assertThat(refundRequests).hasSize(1);
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.provider_cancellation);
    assertThat(lineKinds(adminRow(p.merchant, p.bookingId))).containsExactly("booking_fee_online", "fee_refunded", "total");
  }

  /** A paid fee blocks permanent deletion until the booking is cancelled (refund or keep decided first). */
  @Test
  void bookingWithAPaidFeeMustBeCancelledBeforeDeletion() throws Exception {
    Paid p = paidFeeBooking();
    Map<String, Object> out =
        read(
            mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/m/" + p.merchant.slug + "/admin/salon/bookings/" + p.bookingId), p.merchant.token))
                .andExpect(status().isBadRequest())
                .andReturn());
    assertThat(out.get("error")).isEqualTo("booking_fee_paid_cancel_first");
    assertThat(bookings.findById(p.bookingId)).isPresent();
  }

  /** Customer cancel is server decided and owner checked: wrong email or another client is not found. */
  @Test
  void customerCancelRequiresTheBookingOwner() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    String clientEmail = uniqueEmail("client");
    String clientToken = registerClient(clientEmail);
    Map<String, Object> clientReq = bookingRequest(startIn(Duration.ofDays(3)), "cash_store", clientEmail, null);
    clientReq.put("serviceId", serviceOf(m).toString());
    Map<String, Object> created =
        read(
            mvc.perform(
                    auth(post("/api/public/m/" + m.slug + "/salon/bookings"), clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clientReq)))
                .andExpect(status().isOk())
                .andReturn());
    String bookingId = (String) created.get("bookingId");

    mvc.perform(
            post("/api/public/m/" + m.slug + "/salon/bookings/" + bookingId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("customerEmail", "someone-else@e2e.test"))))
        .andExpect(status().isNotFound());
    String otherToken = registerClient(uniqueEmail("other"));
    mvc.perform(auth(post("/api/clients/me/bookings/" + bookingId + "/cancel"), otherToken))
        .andExpect(status().isNotFound());
    assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().status)
        .isEqualTo(SalonBookingEntity.Status.pending);

    // The owner can cancel from their account; the unpaid fee is simply closed.
    Map<String, Object> out =
        read(
            mvc.perform(auth(post("/api/clients/me/bookings/" + bookingId + "/cancel"), clientToken))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(out.get("bookingFeeOutcome")).isEqualTo("not_paid");
    verify(paystack, never()).refund(any());
    // Cancelling twice is refused.
    mvc.perform(auth(post("/api/clients/me/bookings/" + bookingId + "/cancel"), clientToken))
        .andExpect(status().isBadRequest());
  }

  /** Customers cannot self cancel in-app paid bookings (no refund rule for them) or after the start. */
  @Test
  void customerCannotCancelInAppBookingsOrStartedOnes() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    String email = uniqueEmail("c");
    Map<String, Object> online = book(m, startIn(Duration.ofDays(3)), "online", email, "CARD");
    Map<String, Object> err =
        read(
            mvc.perform(
                    post("/api/public/m/" + m.slug + "/salon/bookings/" + online.get("bookingId") + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerEmail", email))))
                .andExpect(status().isBadRequest())
                .andReturn());
    assertThat(err.get("error")).isEqualTo("booking_not_cancellable");

    Paid p = paidFeeBooking();
    clock.set(p.start.plusSeconds(1));
    cancelAsCustomer(p).andExpect(status().isBadRequest());
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.paid);
  }

  // ================================================================ edge cases 4 and 5, refund webhooks

  /** Edge case 4: a fee paid after the 2 hour window cancelled the booking is refunded; the booking stays cancelled. */
  @Test
  void latePaymentAfterExpiryIsRefundedAutomatically() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    Map<String, Object> created = book(m, startIn(Duration.ofDays(3)), "cash_store", uniqueEmail("c"), null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    String reference = lastInitialize().reference();
    clock.set(clock.instant().plus(Duration.ofHours(3)));
    salonBookingService.expireUnpaidBookingFees(clock.instant(), Duration.ofHours(2));
    assertThat(bookings.findById(bookingId).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.cancelled);

    byte[] body = chargeSuccess(reference, 20_000, bookingId);
    sendWebhook(body).andExpect(status().isOk());
    sendWebhook(body).andExpect(status().isOk()); // replay: never refunded twice

    SalonBookingEntity b = bookings.findById(bookingId).orElseThrow();
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.refunded);
    assertThat(b.bookingFeeReason).isEqualTo(SalonBookingEntity.BookingFeeReason.payment_after_cancellation);
    assertThat(refundRequests).hasSize(1);
    assertThat(refundRequests.get(0).transactionReference()).isEqualTo(reference);
    assertThat(attempts.findByReference(reference).orElseThrow().outcome)
        .isEqualTo(OnlinePaymentAttemptEntity.Outcome.REFUNDED);
    assertThat(owedAttemptIds(m.tenantId)).isEmpty();
  }

  /**
   * Edge case 5: a failed refund stays pending ("refund in progress"), is flagged for support and retried with a
   * backoff; the retry checks Paystack first and is accepted once.
   */
  @Test
  void failedRefundIsFlaggedShownInProgressAndRetried() throws Exception {
    Paid p = paidFeeBooking();
    doThrow(new PaystackClient.PaystackException("Paystack refund failed (HTTP 500)")).when(paystack).refund(any());
    clock.set(p.start.minus(Duration.ofDays(2)));
    cancelAsCustomer(p).andExpect(status().isOk());
    assertThat(read(lastCancel).get("bookingFeeOutcome")).isEqualTo("refund_pending");

    OnlinePaymentRefundEntity r = refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow();
    assertThat(r.status).isEqualTo(OnlinePaymentRefundEntity.Status.PENDING);
    assertThat(r.flaggedForSupport).isTrue();
    assertThat(r.tries).isEqualTo(1);
    assertThat(r.lastError).contains("HTTP 500");
    assertThat(r.nextTryAt).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.refund_pending);
    assertThat(lineKinds(lookup(p))).contains("fee_refund_in_progress");
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_FAILED)).hasSize(1);
    assertThat(owedAttemptIds(p.tenantId)).doesNotContain(attemptId(p));

    // Not due yet: nothing is sent.
    assertThat(refundService.sendDue(10)).isZero();
    // Due: the transaction is checked first (still a success), then the refund is sent and accepted.
    clock.set(clock.instant().plus(Duration.ofMinutes(5)));
    when(paystack.verify(p.reference)).thenReturn(Optional.of(transaction(p.reference, "success")));
    doReturn(new PaystackClient.RefundResult("rf_9", "pending", false)).when(paystack).refund(any());
    assertThat(refundService.sendDue(10)).isEqualTo(1);
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.customer_cancellation);
    verify(paystack, times(2)).refund(any());
    assertThat(refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow().providerRefundId).isEqualTo("rf_9");
  }

  /** NFR-4: a retry never sends a second refund when Paystack already shows the transaction reversed. */
  @Test
  void retryDoesNotRefundTwiceWhenPaystackAlreadyReversedTheTransaction() throws Exception {
    Paid p = paidFeeBooking();
    doThrow(new PaystackClient.PaystackException("Paystack could not be reached")).when(paystack).refund(any());
    clock.set(p.start.minus(Duration.ofDays(2)));
    cancelAsCustomer(p).andExpect(status().isOk());
    clearInvocations(paystack);

    clock.set(clock.instant().plus(Duration.ofHours(1)));
    when(paystack.verify(p.reference)).thenReturn(Optional.of(transaction(p.reference, "reversed")));
    assertThat(refundService.sendDue(10)).isEqualTo(1);
    verify(paystack, never()).refund(any());
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.customer_cancellation);
  }

  /** NFR-4: refund webhooks are idempotent; refund.failed puts an accepted refund back for a flagged retry. */
  @Test
  void refundWebhooksAreIdempotentAndAFailureIsRetried() throws Exception {
    Paid p = paidFeeBooking();
    clock.set(p.start.minus(Duration.ofDays(2)));
    cancelAsCustomer(p).andExpect(status().isOk());
    assertRefunded(p, SalonBookingEntity.BookingFeeReason.customer_cancellation);

    byte[] failed = refundEvent("refund.failed", p.reference);
    sendWebhook(failed).andExpect(status().isOk());
    sendWebhook(failed).andExpect(status().isOk());
    OnlinePaymentRefundEntity r = refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow();
    assertThat(r.status).isEqualTo(OnlinePaymentRefundEntity.Status.PENDING);
    assertThat(r.flaggedForSupport).isTrue();
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_FAILED)).hasSize(1);
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.refund_pending);

    when(paystack.verify(p.reference)).thenReturn(Optional.of(transaction(p.reference, "success")));
    assertThat(refundService.sendDue(10)).isEqualTo(1);
    byte[] processed = refundEvent("refund.processed", p.reference);
    sendWebhook(processed).andExpect(status().isOk());
    sendWebhook(processed).andExpect(status().isOk());
    assertThat(refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow().status)
        .isEqualTo(OnlinePaymentRefundEntity.Status.PROCESSED);
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_PROCESSED)).hasSize(1);
    assertThat(refundRequests).hasSize(2);
    // A forged refund webhook is refused.
    mvc.perform(
            post("/api/payments/paystack/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-paystack-signature", "0".repeat(128))
                .content(failed))
        .andExpect(status().isUnauthorized());
  }

  // ================================================================ provider payouts

  /**
   * A fee already released to the provider is not refunded automatically when it must be refunded: it becomes
   * NEEDS_REFUND for a person, the payout is unchanged, and the customer sees "refund in progress".
   */
  @Test
  void feeAlreadyPaidOutIsFlaggedForAPersonInsteadOfRefundedAutomatically() throws Exception {
    Paid p = paidFeeBooking();
    var preview = payouts.preview(p.tenantId);
    assertThat(preview.payments()).extracting(l -> l.attemptId()).containsExactly(attemptId(p));
    assertThat(preview.summary().grossCents()).isEqualTo(20_000);
    ApiUserPrincipal admin = new ApiUserPrincipal(UUID.randomUUID(), "admin@e2e.test", List.of(Role.PLATFORM_ADMIN), null);
    var released =
        payouts.release(
            new ProviderPayoutService.ReleaseRequest(
                p.tenantId, List.of(attemptId(p)), preview.payableVersion(), null, "EFT-1", null),
            admin);

    clock.set(p.start.minus(Duration.ofHours(1)));
    mvc.perform(
            auth(post("/api/m/" + p.merchant.slug + "/admin/salon/bookings/" + p.bookingId + "/status"), p.merchant.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"cancelled\"}"))
        .andExpect(status().isOk());

    verify(paystack, never()).refund(any());
    assertThat(attempts.findById(attemptId(p)).orElseThrow().outcome)
        .isEqualTo(OnlinePaymentAttemptEntity.Outcome.NEEDS_REFUND);
    OnlinePaymentRefundEntity r = refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow();
    assertThat(r.status).isEqualTo(OnlinePaymentRefundEntity.Status.MANUAL);
    assertThat(r.flaggedForSupport).isTrue();
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_FLAGGED)).singleElement()
        .satisfies(e -> assertThat(e.detail).contains(released.payout().id().toString()));
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.refund_pending);
    assertThat(payouts.detail(released.payout().id()).payout().grossCents()).isEqualTo(20_000);

    // Support refunds it in the Paystack dashboard; Paystack's webhook completes it.
    sendWebhook(refundEvent("refund.processed", p.reference)).andExpect(status().isOk());
    assertThat(bookings.findById(p.bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.refunded);
  }

  /** A paid fee of an active booking is payable at once (commission on its gross), like any in-app payment. */
  @Test
  void paidFeeIsPayableToTheProvider() throws Exception {
    Paid p = paidFeeBooking();
    var owed = earnings.owedFor(p.tenantId);
    assertThat(owed.lines()).singleElement().satisfies(l -> {
      assertThat(l.kind()).isEqualTo("BOOKING");
      assertThat(l.grossCents()).isEqualTo(20_000);
      assertThat(l.recordId()).isEqualTo(p.bookingId);
    });
  }

  // ================================================================ fixtures

  private record Merchant(String slug, String token, UUID tenantId) {}

  private record Paid(Merchant merchant, UUID tenantId, UUID bookingId, String email, String reference, Instant start) {}

  private MvcResult lastCancel;

  private Paid paidFeeBooking() throws Exception {
    Merchant m = feeSalon("1000.00", 20);
    String email = uniqueEmail("c");
    Instant start = startIn(Duration.ofDays(3));
    Map<String, Object> created = book(m, start, "cash_store", email, null);
    UUID bookingId = UUID.fromString((String) created.get("bookingId"));
    String reference = lastInitialize().reference();
    sendWebhook(chargeSuccess(reference, 20_000, bookingId)).andExpect(status().isOk());
    assertThat(bookings.findById(bookingId).orElseThrow().bookingFeeStatus)
        .isEqualTo(SalonBookingEntity.BookingFeeStatus.paid);
    return new Paid(m, m.tenantId, bookingId, email, reference, start);
  }

  private void assertRefunded(Paid p, SalonBookingEntity.BookingFeeReason reason) {
    SalonBookingEntity b = bookings.findById(p.bookingId).orElseThrow();
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
    assertThat(b.bookingFeeStatus).isEqualTo(SalonBookingEntity.BookingFeeStatus.refunded);
    assertThat(b.bookingFeeReason).isEqualTo(reason);
    assertThat(attempts.findById(attemptId(p)).orElseThrow().outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.REFUNDED);
    OnlinePaymentRefundEntity r = refunds.findByPaymentAttemptId(attemptId(p)).orElseThrow();
    assertThat(r.status).isIn(OnlinePaymentRefundEntity.Status.SUBMITTED, OnlinePaymentRefundEntity.Status.PROCESSED);
    assertThat(r.reason).isEqualTo(reason);
    assertThat(r.amountCents).isEqualTo(20_000);
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_REQUESTED)).singleElement()
        .satisfies(e -> assertThat(e.reason).isEqualTo(reason));
    assertThat(events(p.bookingId, BookingFeeEventEntity.Type.REFUND_SUBMITTED)).hasSize(1);
  }

  private UUID attemptId(Paid p) {
    return attempts.findByReference(p.reference).orElseThrow().id;
  }

  private List<UUID> owedAttemptIds(UUID tenantId) {
    return earnings.owedFor(tenantId).lines().stream().map(l -> l.attemptId()).toList();
  }

  private List<BookingFeeEventEntity> events(UUID bookingId, BookingFeeEventEntity.Type type) {
    return feeEvents.findByBookingIdOrderByCreatedAtAsc(bookingId).stream().filter(e -> e.type == type).toList();
  }

  private Merchant feeSalon(String price, int percent) throws Exception {
    Merchant m = registerMerchant();
    salon(m, price);
    setFee(m.token, m, percent).andExpect(status().isOk());
    return m;
  }

  private Merchant registerMerchant() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "merchantName", "Fee Shop " + UUID.randomUUID().toString().substring(0, 8),
                "ownerEmail", uniqueEmail("owner"),
                "ownerPassword", PASSWORD));
    Map<String, Object> out =
        read(mvc.perform(post("/api/auth/register-merchant").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn());
    @SuppressWarnings("unchecked")
    Map<String, Object> tenant = (Map<String, Object>) out.get("tenant");
    // A paid plan (valid by calendar date), so moving the test clock never ends a trial.
    subscriptions.forceActivatePlan(UUID.fromString((String) tenant.get("id")), TenantEntity.SubscriptionPlan.STANDARD);
    return new Merchant((String) out.get("merchantSlug"), (String) out.get("token"), UUID.fromString((String) tenant.get("id")));
  }

  /** One service at {@code price}, one stylist free every day 08:00 to 18:00, cash and online accepted. */
  private void salon(Merchant m, String price) {
    var s = shopSettings.findByTenantId(m.tenantId).orElseGet(() -> ShopSettingsDefaults.newRowForTenant(m.tenantId));
    s.shopType = SalonAccessService.SHOP_SALON_AND_STORE;
    s.openingHoursJson = null;
    s.acceptCustomerCash = true;
    s.acceptCustomerOnline = true;
    shopSettings.save(s);
    SalonStaffEntity staff = new SalonStaffEntity();
    staff.id = UUID.randomUUID();
    staff.tenantId = m.tenantId;
    staff.displayName = "Stylist";
    staff.active = true;
    staff.createdAt = Instant.now();
    salonStaff.save(staff);
    for (int day = 1; day <= 7; day++) {
      SalonStaffAvailabilityEntity w = new SalonStaffAvailabilityEntity();
      w.id = UUID.randomUUID();
      w.tenantId = m.tenantId;
      w.staffId = staff.id;
      w.dayOfWeek = day;
      w.startTime = LocalTime.of(8, 0);
      w.endTime = LocalTime.of(18, 0);
      w.createdAt = Instant.now();
      salonAvailability.save(w);
    }
    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = UUID.randomUUID();
    svc.tenantId = m.tenantId;
    svc.name = "Colour";
    svc.description = "Full colour";
    svc.durationMinutes = 60;
    svc.priceZar = new BigDecimal(price);
    svc.active = true;
    svc.createdAt = Instant.now();
    salonServices.save(svc);
  }

  private UUID serviceOf(Merchant m) {
    return salonServices.findActiveByTenant(m.tenantId).get(0).id;
  }

  /** 10:00 SAST on the day {@code ahead} from the clock. */
  private Instant startIn(Duration ahead) {
    LocalDate day = clock.instant().plus(ahead).atZone(SAST).toLocalDate();
    return day.atTime(10, 0).atZone(SAST).toInstant();
  }

  private ResultActions setFee(String token, Merchant m, int percent) throws Exception {
    return mvc.perform(
        auth(put("/api/m/" + m.slug + "/admin/store/booking-fee"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookingFeePercent\":" + percent + "}"));
  }

  private Map<String, Object> bookingRequest(Instant start, String method, String email, String channel) {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("startAt", start.toString());
    req.put("customerName", "Fee Client");
    req.put("customerPhone", "0820000000");
    req.put("customerEmail", email);
    req.put("paymentMethod", method);
    if (channel != null) req.put("onlinePaymentChannel", channel);
    return req;
  }

  private Map<String, Object> book(Merchant m, Instant start, String method, String email, String channel) throws Exception {
    Map<String, Object> req = bookingRequest(start, method, email, channel);
    req.put("serviceId", serviceOf(m).toString());
    return read(
        mvc.perform(post("/api/public/m/" + m.slug + "/salon/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn());
  }

  private void expectBookingError(Merchant m, Instant start, String method, String code) throws Exception {
    Map<String, Object> req = bookingRequest(start, method, uniqueEmail("x"), null);
    req.put("serviceId", serviceOf(m).toString());
    Map<String, Object> out =
        read(mvc.perform(post("/api/public/m/" + m.slug + "/salon/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isBadRequest()).andReturn());
    assertThat(out.get("error")).isEqualTo(code);
  }

  private ResultActions cancelAsCustomer(Paid p) throws Exception {
    ResultActions r =
        mvc.perform(
            post("/api/public/m/" + p.merchant.slug + "/salon/bookings/" + p.bookingId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("customerEmail", p.email))));
    lastCancel = r.andReturn();
    return r;
  }

  private Map<String, Object> lookup(Paid p) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + p.merchant.slug + "/salon/bookings/" + p.bookingId).param("customerEmail", p.email))
            .andExpect(status().isOk())
            .andReturn());
  }

  private ResultActions noShow(Merchant m, UUID bookingId) throws Exception {
    return mvc.perform(auth(post("/api/m/" + m.slug + "/admin/salon/bookings/" + bookingId + "/no-show"), m.token));
  }

  private ResultActions confirmCash(Merchant m, UUID bookingId, String code) throws Exception {
    return mvc.perform(
        auth(post("/api/m/" + m.slug + "/admin/salon/bookings/" + bookingId + "/confirm-cash"), m.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("code", code))));
  }

  private Map<String, Object> adminRow(Merchant m, UUID bookingId) throws Exception {
    List<Map<String, Object>> rows =
        json.readValue(
            mvc.perform(auth(get("/api/m/" + m.slug + "/admin/salon/bookings"), m.token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            new TypeReference<>() {});
    return rows.stream()
        .filter(r -> bookingId.toString().equals(r.get("id")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("booking not listed"));
  }

  private Map<String, Object> paymentStatus(Merchant m, UUID bookingId, String email) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + m.slug + "/salon/bookings/" + bookingId + "/payment-status").param("customerEmail", email))
            .andExpect(status().isOk())
            .andReturn());
  }

  @SuppressWarnings("unchecked")
  private static List<String> lineKinds(Map<String, Object> m) {
    List<String> out = new ArrayList<>();
    for (Object o : (List<Object>) m.get("receiptLines")) out.add((String) ((Map<String, Object>) o).get("kind"));
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<String> lineAmounts(Map<String, Object> m) {
    List<String> out = new ArrayList<>();
    for (Object o : (List<Object>) m.get("receiptLines")) out.add((String) ((Map<String, Object>) o).get("amountZar"));
    return out;
  }

  private String registerClient(String email) throws Exception {
    String body = json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Fee Client"));
    Map<String, Object> out =
        read(mvc.perform(post("/api/auth/register-client").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn());
    return (String) out.get("token");
  }

  private String login(String email) throws Exception {
    Map<String, Object> out =
        read(
            mvc.perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn());
    return (String) out.get("token");
  }

  private PaystackClient.InitializeRequest lastInitialize() {
    assertThat(initialized).isNotEmpty();
    return initialized.get(initialized.size() - 1);
  }

  private static PaystackClient.Transaction transaction(String reference, String status) {
    return new PaystackClient.Transaction(
        "4099", status, reference, 20_000, "ZAR", "card", "test", Map.of());
  }

  private byte[] chargeSuccess(String reference, long amountCents, UUID bookingId) throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", 4099260516L);
    data.put("domain", "test");
    data.put("status", "success");
    data.put("reference", reference);
    data.put("amount", amountCents);
    data.put("currency", "ZAR");
    data.put("channel", "card");
    data.put("fees", 350);
    data.put("metadata", Map.of("kind", "booking", "id", bookingId.toString()));
    return json.writeValueAsBytes(Map.of("event", "charge.success", "data", data));
  }

  private byte[] refundEvent(String event, String transactionReference) throws Exception {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", event.substring("refund.".length()));
    data.put("transaction_reference", transactionReference);
    data.put("refund_reference", null);
    data.put("amount", "20000");
    data.put("currency", "ZAR");
    data.put("domain", "test");
    return json.writeValueAsBytes(Map.of("event", event, "data", data));
  }

  private ResultActions sendWebhook(byte[] body) throws Exception {
    return mvc.perform(
        post("/api/payments/paystack/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-paystack-signature", sign(body))
            .content(body));
  }

  private static String sign(byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }

  private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
    return b.header("Authorization", "Bearer " + token);
  }

  private Map<String, Object> read(MvcResult r) throws Exception {
    String body = r.getResponse().getContentAsString();
    if (body == null || body.isBlank()) return Map.of();
    return json.readValue(body, new TypeReference<>() {});
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@e2e.test";
  }
}
