package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.EmployeePayrollJobMarkEntity;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeePayrollJobMarkRepository;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.EmployeeService;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Separate employee pay for orders vs services: order pay setting (none / fixed per order / % of order),
 * "% of service price" for service pay, split totals, paid amounts snapshotted when marked, validation and
 * owner-only settings.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeePaySplitIntegrationTest {

  private static final String SLUG = "pay-split";
  private static final String OTHER_SLUG = "pay-split-other";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired EmployeeRepository employees;
  @Autowired EmployeePayrollJobMarkRepository payrollMarks;
  @Autowired OrderRepository orders;
  @Autowired SalonBookingRepository bookings;
  @Autowired SalonServiceRepository services;
  @Autowired SalonStaffRepository salonStaff;
  @Autowired SalonStaffAvailabilityRepository availability;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired EmployeeService employeeService;
  @Autowired PasswordHasher passwordHasher;

  UUID tenantId;
  UUID serviceId;
  String ownerToken;
  String otherOwnerToken;
  String staffId;
  String staffToken;
  final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @BeforeEach
  void seed() throws Exception {
    cleanup();
    tenantId = tenant(SLUG);
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);
    owner(tenantId, "owner-split@test.local");
    ownerToken = login("owner-split@test.local", "Owner@123456");

    UUID other = tenant(OTHER_SLUG);
    subscriptions.forceActivatePlan(other, TenantEntity.SubscriptionPlan.STANDARD);
    owner(other, "owner-split-other@test.local");
    otherOwnerToken = login("owner-split-other@test.local", "Owner@123456");

    SalonServiceEntity s = new SalonServiceEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.name = "Cut";
    s.description = "";
    s.durationMinutes = 60;
    s.priceZar = new BigDecimal("150.00");
    s.active = true;
    s.createdAt = Instant.now();
    services.save(s);
    serviceId = s.id;

    // Created without any order pay fields: order pay defaults to NONE.
    staffId =
        createStaff(
                "{\"email\":\"staff-split@test.local\",\"password\":\"Staff@123456\",\"displayName\":\"Ayanda\","
                    + "\"payMethod\":\"PER_SERVICE\",\"payRate\":100,\"bonusPercentage\":0}")
            .path("id")
            .asText();
    staffToken = login("staff-split@test.local", "Staff@123456");
  }

  @AfterEach
  void cleanup() {
    payrollMarks.deleteAll();
    bookings.deleteAll();
    orders.deleteAll();
    services.deleteAll();
    availability.deleteAll();
    salonStaff.deleteAll();
    employees.deleteAll();
    subscriptionRepository.deleteAll();
    memberships.deleteAll();
    users.deleteAll();
    tenants.findBySlug(SLUG).ifPresent(tenants::delete);
    tenants.findBySlug(OTHER_SLUG).ifPresent(tenants::delete);
  }

  @Test
  void newEmployeesDefaultToNoOrderPay() throws Exception {
    JsonNode member = teamMember(staffId);
    assertThat(member.get("orderPayMethod").asText()).isEqualTo("NONE");
    assertThat(member.get("orderPayRate").isNull()).isTrue();
    assertThat(employees.findById(UUID.fromString(staffId)).orElseThrow().orderPayMethod).isEqualTo("NONE");

    // Completed order credited to them earns nothing and is not listed; service pay is unchanged.
    order(OrderEntity.FulfillmentStatus.completed, "100.00", "0.00");
    booking(SalonBookingEntity.Status.completed, "150.00");
    JsonNode row = payrollRow();
    assertThat(row.get("orderPending").asDouble()).isZero();
    assertThat(row.get("servicePending").asDouble()).isEqualTo(100.0);
    assertThat(row.get("pendingExpected").asDouble()).isEqualTo(100.0);
    assertThat(row.get("jobCount").asInt()).isEqualTo(1);
    row.get("lines").forEach(l -> assertThat(l.get("jobType").asText()).isEqualTo("SALON_BOOKING"));
  }

  @Test
  void existingEmployeesKeepEarningForOrdersWhatTheyEarnedBefore() throws Exception {
    // An employee from before order pay existed: PER_SERVICE @ 150 with a 10% bonus, order pay NULL.
    legacyEmployee("PER_SERVICE", 150.0, 10.0);
    order(OrderEntity.FulfillmentStatus.completed, "800.00", "50.00");

    // Before the carry-over is stored, NULL already reads as the carried-over setting (never NONE).
    JsonNode member = teamMember(staffId);
    assertThat(member.get("orderPayMethod").asText()).isEqualTo("FIXED_PER_ORDER");
    assertThat(member.get("orderPayRate").asDouble()).isEqualTo(150.0);
    assertThat(payrollRow().get("orderPending").asDouble()).isEqualTo(165.0); // 150 + 10%, as before

    assertThat(employeeService.carryOverOrderPay()).isEqualTo(1);
    assertThat(employeeService.carryOverOrderPay()).isZero(); // idempotent
    EmployeeEntity stored = employees.findById(UUID.fromString(staffId)).orElseThrow();
    assertThat(stored.orderPayMethod).isEqualTo("FIXED_PER_ORDER");
    assertThat(stored.orderPayRate).isEqualTo(150.0);
    assertThat(payrollRow().get("orderPending").asDouble()).isEqualTo(165.0);
  }

  @Test
  void carryOverMapsEachServicePayMethod() throws Exception {
    Map<String, String[]> expected =
        Map.of(
            "PER_SERVICE", new String[] {"FIXED_PER_ORDER", "120.0"},
            "PER_HOUR", new String[] {"FIXED_PER_ORDER", "120.0"},
            "PER_DAY", new String[] {"NONE", null},
            "WEEKLY", new String[] {"NONE", null},
            "MONTHLY", new String[] {"NONE", null});
    for (var e : expected.entrySet()) {
      legacyEmployee(e.getKey(), 120.0, 0.0);
      assertThat(employeeService.carryOverOrderPay()).as(e.getKey()).isEqualTo(1);
      EmployeeEntity stored = employees.findById(UUID.fromString(staffId)).orElseThrow();
      assertThat(stored.orderPayMethod).as(e.getKey()).isEqualTo(e.getValue()[0]);
      assertThat(stored.orderPayRate == null ? null : stored.orderPayRate.toString())
          .as(e.getKey())
          .isEqualTo(e.getValue()[1]);
    }
  }

  @Test
  void changingServicePayBeforeTheCarryOverRunsKeepsTheOldOrderPay() throws Exception {
    legacyEmployee("PER_SERVICE", 150.0, 0.0);
    // Owner switches service pay to a salary; orders keep the R150 they earned under PER_SERVICE.
    updateStaff("{\"payMethod\":\"MONTHLY\",\"payRate\":6000}").andExpect(status().isOk());
    EmployeeEntity stored = employees.findById(UUID.fromString(staffId)).orElseThrow();
    assertThat(stored.orderPayMethod).isEqualTo("FIXED_PER_ORDER");
    assertThat(stored.orderPayRate).isEqualTo(150.0);
    assertThat(employeeService.carryOverOrderPay()).isZero();
  }

  @Test
  void fixedOrderPayCountsOnlyCompletedNonCancelledOrders() throws Exception {
    updateStaff("{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":40}").andExpect(status().isOk());

    UUID completed = order(OrderEntity.FulfillmentStatus.completed, "300.00", "50.00");
    UUID processing = order(OrderEntity.FulfillmentStatus.processing, "300.00", "50.00");
    UUID ready = order(OrderEntity.FulfillmentStatus.ready, "300.00", "50.00");
    UUID cancelled = order(OrderEntity.FulfillmentStatus.completed, "300.00", "50.00");
    OrderEntity c = orders.findById(cancelled).orElseThrow();
    c.status = OrderEntity.OrderStatus.cancelled;
    c.cancelledAt = Instant.now();
    orders.save(c);

    JsonNode row = payrollRow();
    assertThat(lineIds(row)).containsOnlyKeys(completed.toString());
    assertThat(row.get("orderPending").asDouble()).isEqualTo(40.0);
    assertThat(row.get("pendingExpected").asDouble()).isEqualTo(40.0);

    // Paid but not completed yet: Upcoming on My income, never on payroll.
    JsonNode income = myIncome();
    Map<String, String> statuses = new HashMap<>();
    income.get("lines").forEach(l -> statuses.put(l.get("jobId").asText(), l.get("status").asText()));
    assertThat(statuses)
        .containsOnly(
            Map.entry(completed.toString(), "OWED"),
            Map.entry(processing.toString(), "UPCOMING"),
            Map.entry(ready.toString(), "UPCOMING"));
    assertThat(income.get("owedOrderTotal").asDouble()).isEqualTo(40.0);
    assertThat(income.get("upcomingOrderTotal").asDouble()).isEqualTo(80.0);
    assertThat(income.get("upcomingServiceTotal").asDouble()).isZero();

    // An order that is not completed (or cancelled) cannot be marked paid.
    markPaid(processing, "ORDER").andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("job_not_attributed"));
    markPaid(cancelled, "ORDER").andExpect(status().isConflict());
  }

  @Test
  void percentOfOrderUsesTotalAfterDiscountLessDeliveryAndSplitsTotals() throws Exception {
    updateStaff("{\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":12.5}").andExpect(status().isOk());
    // subtotal 220, discount 20, delivery 30: total 230, pay base 200 -> 12.5% = 25.00
    order(OrderEntity.FulfillmentStatus.completed, "230.00", "30.00");
    // 12.5% of 10.05 = 1.25625 -> 1.26 (half up)
    order(OrderEntity.FulfillmentStatus.completed, "10.05", "0.00");
    booking(SalonBookingEntity.Status.completed, "150.00");

    JsonNode row = payrollRow();
    assertThat(row.get("orderPayMethod").asText()).isEqualTo("PERCENT_OF_ORDER");
    assertThat(row.get("orderPayRate").asDouble()).isEqualTo(12.5);
    assertThat(row.get("orderPending").asDouble()).isEqualTo(26.26);
    assertThat(row.get("servicePending").asDouble()).isEqualTo(100.0);
    assertThat(row.get("pendingExpected").asDouble()).isEqualTo(126.26);
    assertThat(row.get("jobCount").asInt()).isEqualTo(3);

    JsonNode income = myIncome();
    assertThat(income.get("owedOrderTotal").asDouble()).isEqualTo(26.26);
    assertThat(income.get("owedServiceTotal").asDouble()).isEqualTo(100.0);
    assertThat(income.get("owedTotal").asDouble()).isEqualTo(126.26);
    assertThat(income.get("pendingExpected").asDouble()).isEqualTo(126.26);
  }

  @Test
  void percentOfServicePriceUsesTheBookingAmount() throws Exception {
    updateStaff("{\"payMethod\":\"PERCENT_OF_SERVICE\",\"payRate\":40}").andExpect(status().isOk());
    booking(SalonBookingEntity.Status.completed, "150.00"); // 60.00
    booking(SalonBookingEntity.Status.confirmed, "99.99"); // 39.996 -> 40.00

    JsonNode row = payrollRow();
    assertThat(row.get("payMethod").asText()).isEqualTo("PERCENT_OF_SERVICE");
    assertThat(row.get("servicePending").asDouble()).isEqualTo(100.0);
    assertThat(row.get("orderPending").asDouble()).isZero();
  }

  @Test
  void markedPayoutsKeepTheirAmountWhenSettingsChange() throws Exception {
    updateStaff("{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":50}").andExpect(status().isOk());
    UUID paidOrder = order(OrderEntity.FulfillmentStatus.completed, "400.00", "0.00");
    UUID paidBooking = booking(SalonBookingEntity.Status.completed, "150.00");
    markPaid(paidOrder, "ORDER").andExpect(status().isOk());
    markPaid(paidBooking, "SALON_BOOKING").andExpect(status().isOk());
    UUID unpaidOrder = order(OrderEntity.FulfillmentStatus.completed, "400.00", "0.00");

    // Both settings change: only unpaid work is repriced.
    updateStaff(
            "{\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":10,"
                + "\"payMethod\":\"PERCENT_OF_SERVICE\",\"payRate\":20}")
        .andExpect(status().isOk());
    JsonNode row = payrollRow();
    Map<String, JsonNode> lines = lineIds(row);
    assertThat(lines.get(paidOrder.toString()).get("expectedAmount").asDouble()).isEqualTo(50.0);
    assertThat(lines.get(paidBooking.toString()).get("expectedAmount").asDouble()).isEqualTo(100.0);
    assertThat(lines.get(unpaidOrder.toString()).get("expectedAmount").asDouble()).isEqualTo(40.0);
    assertThat(row.get("orderPending").asDouble()).isEqualTo(40.0);

    JsonNode income = myIncome();
    assertThat(income.get("paidOutOrderTotal").asDouble()).isEqualTo(50.0);
    assertThat(income.get("paidOutServiceTotal").asDouble()).isEqualTo(100.0);
    assertThat(income.get("paidOutTotal").asDouble()).isEqualTo(150.0);

    // Turning order pay off still shows the order payout already made.
    updateStaff("{\"orderPayMethod\":\"NONE\"}").andExpect(status().isOk());
    JsonNode afterOff = myIncome();
    assertThat(afterOff.get("paidOutOrderTotal").asDouble()).isEqualTo(50.0);
    assertThat(afterOff.get("owedOrderTotal").asDouble()).isZero();
    assertThat(lineIds(payrollRow())).containsKey(paidOrder.toString()).doesNotContainKey(unpaidOrder.toString());

    EmployeePayrollJobMarkEntity mark =
        payrollMarks
            .findByTenantIdAndEmployeeRecordIdAndJobIdAndJobType(
                tenantId, UUID.fromString(staffId), paidOrder, "ORDER")
            .orElseThrow();
    assertThat(mark.paidAmount).isEqualTo(50.0);
    assertThat(mark.jobAt).isNotNull();
  }

  @Test
  void payAllSnapshotsEachJobAndClearsBothKinds() throws Exception {
    updateStaff("{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":30}").andExpect(status().isOk());
    order(OrderEntity.FulfillmentStatus.completed, "100.00", "0.00");
    booking(SalonBookingEntity.Status.completed, "150.00");

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/team/payroll-marks/pay-all")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":\"" + staffId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.marked").value(2));
    assertThat(payrollMarks.findByTenantIdAndEmployeeRecordId(tenantId, UUID.fromString(staffId)))
        .extracting(m -> m.paidAmount)
        .containsExactlyInAnyOrder(30.0, 100.0);
    JsonNode income = myIncome();
    assertThat(income.get("owedTotal").asDouble()).isZero();
    assertThat(income.get("paidOutTotal").asDouble()).isEqualTo(130.0);
  }

  @Test
  void monthlySalaryIsSnapshottedOnceOnTheMarkThatClearsIt() throws Exception {
    updateStaff("{\"payMethod\":\"MONTHLY\",\"payRate\":5000}").andExpect(status().isOk());
    UUID first = booking(SalonBookingEntity.Status.completed, "150.00");
    UUID second = booking(SalonBookingEntity.Status.completed, "150.00");
    assertThat(payrollRow().get("servicePending").asDouble()).isEqualTo(5000.0);

    markPaid(first, "SALON_BOOKING").andExpect(status().isOk());
    // Salary is still owed while a job is unpaid; this mark paid nothing of it yet.
    assertThat(payrollRow().get("servicePending").asDouble()).isEqualTo(5000.0);
    markPaid(second, "SALON_BOOKING").andExpect(status().isOk());
    assertThat(payrollRow().get("servicePending").asDouble()).isZero();

    updateStaff("{\"payRate\":9000}").andExpect(status().isOk());
    JsonNode income = myIncome();
    assertThat(income.get("paidOutServiceTotal").asDouble()).isEqualTo(5000.0);
    assertThat(income.get("owedTotal").asDouble()).isZero();
  }

  @Test
  void legacyMarksAreSnapshottedWithTheOldRules() throws Exception {
    // Before the split, a credited paid order earned service pay (PER_SERVICE @ 100) whatever its fulfilment.
    UUID legacyOrder = order(OrderEntity.FulfillmentStatus.processing, "500.00", "0.00");
    EmployeePayrollJobMarkEntity m = new EmployeePayrollJobMarkEntity();
    m.tenantId = tenantId;
    m.employeeRecordId = UUID.fromString(staffId);
    m.jobId = legacyOrder;
    m.jobType = "ORDER";
    payrollMarks.save(m);

    assertThat(employeeService.snapshotLegacyMarks()).isEqualTo(1);
    assertThat(employeeService.snapshotLegacyMarks()).isZero(); // idempotent
    EmployeePayrollJobMarkEntity saved = payrollMarks.findById(m.id).orElseThrow();
    assertThat(saved.paidAmount).isEqualTo(100.0);

    // Order pay is NONE and the order is not completed, yet the payout already made still shows.
    JsonNode income = myIncome();
    assertThat(income.get("paidOutOrderTotal").asDouble()).isEqualTo(100.0);
    assertThat(income.get("owedTotal").asDouble()).isZero();
  }

  @Test
  void rejectsInvalidPaySettings() throws Exception {
    expectError(updateStaff("{\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":100.01}"), "invalid_order_pay_rate");
    expectError(updateStaff("{\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":12.345}"), "invalid_order_pay_rate");
    expectError(updateStaff("{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":-1}"), "invalid_order_pay_rate");
    expectError(updateStaff("{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":\"abc\"}"), "invalid_order_pay_rate");
    expectError(updateStaff("{\"orderPayMethod\":\"PER_HOUR\",\"orderPayRate\":10}"), "invalid_order_pay_method");
    expectError(updateStaff("{\"payMethod\":\"PERCENT_OF_SERVICE\",\"payRate\":150}"), "invalid_pay_rate");
    expectError(updateStaff("{\"payRate\":-10}"), "invalid_pay_rate");
    expectError(updateStaff("{\"payRate\":\"ten\"}"), "invalid_pay_rate");
    expectError(
        mvc.perform(
            post("/api/m/" + SLUG + "/admin/team")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"bad@test.local\",\"password\":\"Staff@123456\",\"payMethod\":\"PER_SERVICE\","
                        + "\"payRate\":10,\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":101}")),
        "invalid_order_pay_rate");

    // Nothing was saved by the rejected requests.
    JsonNode member = teamMember(staffId);
    assertThat(member.get("orderPayMethod").asText()).isEqualTo("NONE");
    assertThat(member.get("payMethod").asText()).isEqualTo("PER_SERVICE");
    assertThat(member.get("payRate").asDouble()).isEqualTo(100.0);

    // Boundaries that are allowed.
    updateStaff("{\"orderPayMethod\":\"PERCENT_OF_ORDER\",\"orderPayRate\":100}").andExpect(status().isOk());
    updateStaff("{\"orderPayRate\":0}").andExpect(status().isOk());
    updateStaff("{\"orderPayRate\":12.34}").andExpect(status().isOk());
    updateStaff("{\"payMethod\":\"PERCENT_OF_SERVICE\"}").andExpect(status().isOk());
    member = teamMember(staffId);
    assertThat(member.get("orderPayRate").asDouble()).isEqualTo(12.34);
    assertThat(member.get("payMethod").asText()).isEqualTo("PERCENT_OF_SERVICE");
  }

  @Test
  void onlyTheOwnerOfThisStoreCanChangePaySettings() throws Exception {
    String body = "{\"orderPayMethod\":\"FIXED_PER_ORDER\",\"orderPayRate\":999}";
    // Staff member (even on their own record).
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/team/" + staffId)
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    // Another store's owner, through this store and through their own store.
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/team/" + staffId)
                .header("Authorization", "Bearer " + otherOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/m/" + OTHER_SLUG + "/admin/team/" + staffId)
                .header("Authorization", "Bearer " + otherOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is4xxClientError())
        .andExpect(jsonPath("$.error").value("employee_not_found"));
    // No token.
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/team/" + staffId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is4xxClientError());

    assertThat(teamMember(staffId).get("orderPayMethod").asText()).isEqualTo("NONE");
  }

  // --- helpers ---

  /** Turns the seeded staff member into a row from before order pay existed (order_pay_method NULL). */
  private void legacyEmployee(String payMethod, Double payRate, Double bonus) {
    EmployeeEntity e = employees.findById(UUID.fromString(staffId)).orElseThrow();
    e.payMethod = payMethod;
    e.payRate = payRate;
    e.bonusPercentage = bonus;
    e.orderPayMethod = null;
    e.orderPayRate = null;
    employees.save(e);
  }

  private ResultActions updateStaff(String body) throws Exception {
    return mvc.perform(
        put("/api/m/" + SLUG + "/admin/team/" + staffId)
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static void expectError(ResultActions res, String code) throws Exception {
    res.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(code));
  }

  private ResultActions markPaid(UUID jobId, String jobType) throws Exception {
    return mvc.perform(
        post("/api/m/" + SLUG + "/admin/team/payroll-marks")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"employeeId\":\"" + staffId + "\",\"jobId\":\"" + jobId + "\",\"jobType\":\"" + jobType
                    + "\"}"));
  }

  private UUID order(OrderEntity.FulfillmentStatus fulfillment, String total, String deliveryFee) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.createdAt = now;
    o.customerName = "C";
    o.customerEmail = "c@test.local";
    o.deliveryType =
        new BigDecimal(deliveryFee).signum() > 0
            ? OrderEntity.DeliveryType.delivery
            : OrderEntity.DeliveryType.pickup;
    o.deliveryFeeZar = new BigDecimal(deliveryFee);
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    o.status = OrderEntity.OrderStatus.paid;
    o.fulfillmentStatus = fulfillment;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    o.subtotalZar = new BigDecimal(total).subtract(new BigDecimal(deliveryFee));
    o.totalZar = new BigDecimal(total);
    o.paymentConfirmedAt = now;
    o.completedAt = now;
    o.completedByEmployeeId = UUID.fromString(staffId);
    orders.save(o);
    return o.id;
  }

  private UUID booking(SalonBookingEntity.Status st, String amount) {
    SalonBookingEntity e = new SalonBookingEntity();
    e.id = UUID.randomUUID();
    e.tenantId = tenantId;
    e.serviceId = serviceId;
    e.customerName = "Client";
    e.customerPhone = "0820000000";
    e.customerEmail = "client@test.local";
    e.startAt = now.minus(1, ChronoUnit.DAYS);
    e.endAt = e.startAt.plus(1, ChronoUnit.HOURS);
    e.status = st;
    e.amountZar = new BigDecimal(amount);
    e.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    e.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    e.createdAt = now;
    e.completedByEmployeeId = UUID.fromString(staffId);
    e.completedAt = e.startAt;
    bookings.save(e);
    return e.id;
  }

  private JsonNode payrollRow() throws Exception {
    JsonNode res = getJson("/api/m/" + SLUG + "/admin/team/payment-calculations", ownerToken);
    for (JsonNode row : res.get("calculations")) {
      if (staffId.equals(row.get("employeeId").asText())) return row;
    }
    throw new AssertionError("no payroll row");
  }

  private JsonNode myIncome() throws Exception {
    return getJson("/api/m/" + SLUG + "/admin/team/my-expected-income", staffToken);
  }

  private JsonNode teamMember(String id) throws Exception {
    for (JsonNode m : getJson("/api/m/" + SLUG + "/admin/team", ownerToken).get("team")) {
      if (id.equals(m.get("id").asText())) return m;
    }
    throw new AssertionError("no team member " + id);
  }

  private static Map<String, JsonNode> lineIds(JsonNode row) {
    Map<String, JsonNode> out = new HashMap<>();
    row.get("lines").forEach(l -> out.put(l.get("jobId").asText(), l));
    return out;
  }

  private JsonNode getJson(String url, String token) throws Exception {
    String res =
        mvc.perform(get(url).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res);
  }

  private JsonNode createStaff(String body) throws Exception {
    String res =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(res).path("member");
  }

  private UUID tenant(String slug) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = slug;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    return t.id;
  }

  private void owner(UUID tenant, String email) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash("Owner@123456");
    u.createdAt = Instant.now();
    users.save(u);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = tenant;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    memberships.save(m);
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
}
