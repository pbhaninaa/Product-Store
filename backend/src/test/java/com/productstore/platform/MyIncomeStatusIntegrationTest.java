package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonBookingEntity.PaymentVerificationState;
import com.productstore.platform.entities.SalonBookingEntity.Status;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeePayrollJobMarkRepository;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Staff "My income": every booking is Upcoming, Owed or Paid out (or not shown), and Upcoming work never
 * reaches the owner's payroll.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyIncomeStatusIntegrationTest {

  private static final String SLUG = "my-income-salon";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired EmployeeRepository employees;
  @Autowired EmployeePayrollJobMarkRepository payrollMarks;
  @Autowired SalonStaffRepository salonStaff;
  @Autowired SalonStaffAvailabilityRepository availability;
  @Autowired SalonServiceRepository services;
  @Autowired SalonBookingRepository bookings;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired PasswordHasher passwordHasher;

  UUID tenantId;
  UUID serviceId;
  String ownerToken;
  String lindiweId;
  UUID lindiweStaffId;
  String lindiweToken;
  UUID otherId;
  UUID otherStaffId;
  final Map<String, UUID> b = new HashMap<>();
  final Instant now = Instant.now();

  @BeforeEach
  void seed() throws Exception {
    cleanup();
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "My Income Salon";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    tenantId = t.id;
    shopSettings.save(salonSettings());
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-income@test.local";
    owner.passwordHash = passwordHasher.hash("Owner@123456");
    owner.createdAt = Instant.now();
    users.save(owner);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = owner.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    memberships.save(m);
    ownerToken = login("owner-income@test.local", "Owner@123456");

    SalonServiceEntity s = new SalonServiceEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.name = "Braids";
    s.description = "";
    s.durationMinutes = 60;
    s.priceZar = new BigDecimal("150.00");
    s.active = true;
    s.createdAt = Instant.now();
    services.save(s);
    serviceId = s.id;

    lindiweId = createStaff("lindiwe-income@test.local", "Lindiwe");
    lindiweToken = login("lindiwe-income@test.local", "Staff@123456");
    lindiweStaffId = staffFor(lindiweId);
    otherId = UUID.fromString(createStaff("sipho-income@test.local", "Sipho"));
    otherStaffId = staffFor(otherId.toString());
    UUID lindiwe = UUID.fromString(lindiweId);

    // Owed: finished and credited to Lindiwe, not marked paid.
    b.put("owed", booking(Status.completed, lindiweStaffId, lindiwe, days(-5)));
    // Paid out: credited to Lindiwe and marked paid below.
    b.put("paid", booking(Status.completed, lindiweStaffId, lindiwe, days(-6)));
    // Upcoming: booked with Lindiwe, awaiting payment / not credited yet.
    b.put("upPending", booking(Status.pending, lindiweStaffId, null, days(3)));
    b.put("upConfirmed", booking(Status.confirmed, lindiweStaffId, null, days(10)));
    // Not shown to Lindiwe at all.
    b.put("cancelled", booking(Status.cancelled, lindiweStaffId, null, days(4)));
    UUID rejected = booking(Status.pending, lindiweStaffId, null, days(5));
    SalonBookingEntity r = bookings.findById(rejected).orElseThrow();
    r.paymentVerificationState = PaymentVerificationState.manual_rejected;
    bookings.save(r);
    b.put("stalePending", booking(Status.pending, lindiweStaffId, null, days(-2)));
    b.put("othersCredited", booking(Status.confirmed, lindiweStaffId, otherId, days(-1)));
    b.put("othersUpcoming", booking(Status.pending, otherStaffId, null, days(3)));

    markPaid(lindiweId, b.get("paid"));
  }

  @AfterEach
  void cleanup() {
    payrollMarks.deleteAll();
    bookings.deleteAll();
    services.deleteAll();
    availability.deleteAll();
    salonStaff.deleteAll();
    employees.deleteAll();
    shopSettings.deleteAll();
    subscriptionRepository.deleteAll();
    memberships.deleteAll();
    users.deleteAll();
    tenants.findBySlug(SLUG).ifPresent(tenants::delete);
  }

  @Test
  void classifiesEveryJobAsUpcomingOwedOrPaidOut() throws Exception {
    JsonNode res = myIncome(lindiweToken, null, null);

    assertThat(statusById(res))
        .containsOnly(
            Map.entry(b.get("upConfirmed").toString(), "UPCOMING"),
            Map.entry(b.get("upPending").toString(), "UPCOMING"),
            Map.entry(b.get("owed").toString(), "OWED"),
            Map.entry(b.get("paid").toString(), "PAID_OUT"));
    // Upcoming first, then newest first.
    assertThat(jobIds(res))
        .containsExactly(
            b.get("upConfirmed").toString(),
            b.get("upPending").toString(),
            b.get("owed").toString(),
            b.get("paid").toString());

    // PER_SERVICE @ R100 per job.
    assertThat(res.get("owedTotal").asDouble()).isEqualTo(100.0);
    assertThat(res.get("owedCount").asInt()).isEqualTo(1);
    assertThat(res.get("pendingExpected").asDouble()).isEqualTo(100.0);
    assertThat(res.get("upcomingTotal").asDouble()).isEqualTo(200.0);
    assertThat(res.get("upcomingCount").asInt()).isEqualTo(2);
    assertThat(res.get("paidOutTotal").asDouble()).isEqualTo(100.0);
    for (JsonNode line : res.get("lines")) {
      assertThat(line.get("employerPaid").asBoolean())
          .isEqualTo("PAID_OUT".equals(line.get("status").asText()));
    }
  }

  @Test
  void dateFilterAppliesToAllThreeGroups() throws Exception {
    LocalDate upDay = LocalDate.ofInstant(days(3), ZoneOffset.UTC);
    JsonNode onlyUpcoming = myIncome(lindiweToken, upDay, upDay);
    assertThat(jobIds(onlyUpcoming)).containsExactly(b.get("upPending").toString());
    assertThat(onlyUpcoming.get("upcomingCount").asInt()).isEqualTo(1);
    assertThat(onlyUpcoming.get("owedCount").asInt()).isZero();
    assertThat(onlyUpcoming.get("paidOutTotal").asDouble()).isZero();

    LocalDate pastFrom = LocalDate.ofInstant(days(-6), ZoneOffset.UTC);
    LocalDate pastTo = LocalDate.ofInstant(days(-5), ZoneOffset.UTC);
    JsonNode onlyPast = myIncome(lindiweToken, pastFrom, pastTo);
    assertThat(statusById(onlyPast))
        .containsOnly(
            Map.entry(b.get("owed").toString(), "OWED"),
            Map.entry(b.get("paid").toString(), "PAID_OUT"));
    assertThat(onlyPast.get("upcomingCount").asInt()).isZero();
    assertThat(onlyPast.get("upcomingTotal").asDouble()).isZero();
  }

  @Test
  void ownerPayrollAndPayAllIgnoreUpcomingJobs() throws Exception {
    JsonNode row = payrollRow(lindiweId);
    assertThat(row.get("jobCount").asInt()).isEqualTo(1);
    assertThat(row.get("pendingExpected").asDouble()).isEqualTo(100.0);
    List<String> payrollJobs = new ArrayList<>();
    row.get("lines").forEach(l -> payrollJobs.add(l.get("jobId").asText()));
    assertThat(payrollJobs)
        .containsExactlyInAnyOrder(b.get("owed").toString(), b.get("paid").toString());
    row.get("lines").forEach(l -> assertThat(l.has("status")).isFalse());

    // An upcoming booking cannot be marked paid.
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/team/payroll-marks")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"employeeId\":\"" + lindiweId + "\",\"jobId\":\"" + b.get("upPending")
                        + "\",\"jobType\":\"SALON_BOOKING\"}"))
        .andExpect(status().isConflict());

    // Pay all pays only the owed job.
    String payAll =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team/payroll-marks/pay-all")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeId\":\"" + lindiweId + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(payAll).get("marked").asInt()).isEqualTo(1);
    assertThat(payrollMarks.findByTenantIdAndEmployeeRecordId(tenantId, UUID.fromString(lindiweId)))
        .extracting(mk -> mk.jobId)
        .containsExactlyInAnyOrder(b.get("owed"), b.get("paid"));
    assertThat(payrollRow(lindiweId).get("pendingExpected").asDouble()).isZero();

    JsonNode after = myIncome(lindiweToken, null, null);
    assertThat(after.get("owedTotal").asDouble()).isZero();
    assertThat(after.get("paidOutTotal").asDouble()).isEqualTo(200.0);
    assertThat(after.get("upcomingCount").asInt()).isEqualTo(2);
  }

  @Test
  void ownerWithoutEmployeeRecordGetsAnEmptyResult() throws Exception {
    JsonNode res = myIncome(ownerToken, null, null);
    assertThat(res.get("employeeId").isNull()).isTrue();
    assertThat(res.get("lines")).isEmpty();
    assertThat(res.get("upcomingCount").asInt()).isZero();
  }

  // --- helpers ---

  private Instant days(long n) {
    return now.plus(n, ChronoUnit.DAYS);
  }

  private UUID booking(Status st, UUID staffId, UUID creditedTo, Instant start) {
    SalonBookingEntity e = new SalonBookingEntity();
    e.id = UUID.randomUUID();
    e.tenantId = tenantId;
    e.serviceId = serviceId;
    e.staffId = staffId;
    e.customerName = "Client";
    e.customerPhone = "0820000000";
    e.customerEmail = "client@test.local";
    e.startAt = start;
    e.endAt = start.plus(1, ChronoUnit.HOURS);
    e.status = st;
    e.amountZar = new BigDecimal("150.00");
    e.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    e.paymentVerificationState = PaymentVerificationState.not_applicable;
    e.createdAt = Instant.now();
    e.completedByEmployeeId = creditedTo;
    e.completedAt = creditedTo != null ? start : null;
    bookings.save(e);
    return e.id;
  }

  private UUID staffFor(String employeeId) {
    return salonStaff
        .findByTenantIdAndEmployeeId(tenantId, UUID.fromString(employeeId))
        .orElseThrow()
        .id;
  }

  private void markPaid(String employeeId, UUID jobId) throws Exception {
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/team/payroll-marks")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"employeeId\":\"" + employeeId + "\",\"jobId\":\"" + jobId
                        + "\",\"jobType\":\"SALON_BOOKING\"}"))
        .andExpect(status().isOk());
  }

  private JsonNode myIncome(String token, LocalDate from, LocalDate to) throws Exception {
    String q = from == null ? "" : "?startDate=" + from + "&endDate=" + to;
    String res =
        mvc.perform(
                get("/api/m/" + SLUG + "/admin/team/my-expected-income" + q)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res);
  }

  private static Map<String, String> statusById(JsonNode res) {
    Map<String, String> out = new HashMap<>();
    res.get("lines").forEach(l -> out.put(l.get("jobId").asText(), l.get("status").asText()));
    return out;
  }

  private static List<String> jobIds(JsonNode res) {
    List<String> out = new ArrayList<>();
    res.get("lines").forEach(l -> out.add(l.get("jobId").asText()));
    return out;
  }

  private JsonNode payrollRow(String employeeId) throws Exception {
    String res =
        mvc.perform(
                get("/api/m/" + SLUG + "/admin/team/payment-calculations")
                    .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (JsonNode row : json.readTree(res).get("calculations")) {
      if (employeeId.equals(row.get("employeeId").asText())) return row;
    }
    throw new AssertionError("no payroll row for " + employeeId);
  }

  private String createStaff(String email, String name) throws Exception {
    String res =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + email + "\",\"password\":\"Staff@123456\",\"displayName\":\"" + name
                            + "\",\"role\":\"STAFF\",\"payMethod\":\"PER_SERVICE\",\"payRate\":100,"
                            + "\"bonusPercentage\":0}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res).path("member").path("id").asText();
  }

  private String login(String email, String password) throws Exception {
    String res =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res).get("token").asText();
  }

  private ShopSettingsEntity salonSettings() {
    Instant ts = Instant.now();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = "";
    s.bankAccountHolder = "";
    s.bankAccountNumber = "";
    s.bankBranchCode = "";
    s.storeName = "Salon";
    s.contactEmail = "a@b.co";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "salon_only";
    s.openingHoursJson = "[]";
    s.acceptCustomerPeach = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = ts;
    s.updatedAt = ts;
    return s;
  }
}
