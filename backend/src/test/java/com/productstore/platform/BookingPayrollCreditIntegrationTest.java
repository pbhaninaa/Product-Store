package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.SalonBookingEntity;
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

/** A booking a team member confirms, starts or completes counts towards that person's payroll. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingPayrollCreditIntegrationTest {

  private static final String SLUG = "payroll-credit-salon";
  /** The customer's handover code: staff enter it to start the service. */
  private static final String CODE = "135790";

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

  @BeforeEach
  void seed() throws Exception {
    cleanup();
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Payroll Credit Salon";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    tenantId = t.id;
    shopSettings.save(salonSettings());
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-credit@test.local";
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
    ownerToken = login("owner-credit@test.local", "Owner@123456");

    SalonServiceEntity s = new SalonServiceEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.name = "Haircut";
    s.description = "";
    s.durationMinutes = 60;
    s.priceZar = new BigDecimal("150.00");
    s.active = true;
    s.createdAt = Instant.now();
    services.save(s);
    serviceId = s.id;
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
  void staffWhoStartsAndCompletesABookingIsCreditedForPayroll() throws Exception {
    String employeeId = createStaff("bayanda-credit@test.local", "Bayanda");
    String staffToken = login("bayanda-credit@test.local", "Staff@123456");
    UUID booking = paidBooking();

    setStatus(staffToken, booking, "in_progress");
    setStatus(staffToken, booking, "completed");

    SalonBookingEntity b = bookings.findById(booking).orElseThrow();
    assertThat(b.completedByEmployeeId).hasToString(employeeId);
    JsonNode row = payrollRow(employeeId);
    assertThat(row.get("jobCount").asInt()).isEqualTo(1);
    assertThat(row.get("pendingExpected").asDouble()).isEqualTo(100.0); // per service @ R100
  }

  @Test
  void ownerActionsDoNotCreditAnyoneAndNeverOverrideAnAssignment() throws Exception {
    String employeeId = createStaff("thabo-credit@test.local", "Thabo");
    String staffToken = login("thabo-credit@test.local", "Staff@123456");

    // Owner has no employee record: moving the booking along leaves it for the owner to assign.
    UUID ownerBooking = paidBooking();
    setStatus(ownerToken, ownerBooking, "completed");
    assertThat(bookings.findById(ownerBooking).orElseThrow().completedByEmployeeId).isNull();

    // Already credited to someone else: a later action by another team member doesn't take it.
    UUID assigned = paidBooking();
    UUID someoneElse = UUID.fromString(createStaff("lerato-credit@test.local", "Lerato"));
    SalonBookingEntity a = bookings.findById(assigned).orElseThrow();
    a.completedByEmployeeId = someoneElse;
    bookings.save(a);
    setStatus(staffToken, assigned, "completed");
    assertThat(bookings.findById(assigned).orElseThrow().completedByEmployeeId).isEqualTo(someoneElse);
    assertThat(payrollRow(employeeId).get("jobCount").asInt()).isZero();
  }

  private void setStatus(String token, UUID booking, String status) throws Exception {
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/salon/bookings/" + booking + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"code\":\"" + CODE + "\"}"))
        .andExpect(status().isOk());
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

  /** A cash booking already confirmed with the customer's code (i.e. paid). */
  private UUID paidBooking() {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.customerName = "Client";
    b.customerPhone = "0820000000";
    b.customerEmail = "client@test.local";
    b.startAt = Instant.now().minus(2, ChronoUnit.HOURS);
    b.endAt = b.startAt.plus(1, ChronoUnit.HOURS);
    b.status = SalonBookingEntity.Status.confirmed;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.cash_store;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    b.handoverCode = CODE;
    b.createdAt = Instant.now();
    bookings.save(b);
    return b.id;
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
    Instant now = Instant.now();
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
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    return s;
  }
}
