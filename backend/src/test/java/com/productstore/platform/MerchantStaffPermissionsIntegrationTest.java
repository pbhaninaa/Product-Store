package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
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

/** Per-staff merchant permissions: defaults, enforcement on the API, owner-only actions, and store isolation. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantStaffPermissionsIntegrationTest {
  private static final String A = "perm-store-a";
  private static final String B = "perm-store-b";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired EmployeeRepository employeeRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired PasswordHasher passwordHasher;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;

  String ownerAToken;
  String ownerBToken;
  String staffToken;
  String staffEmployeeId;

  @BeforeEach
  void seed() throws Exception {
    orderRepository.deleteAll();
    employeeRepository.deleteAll();
    subscriptionRepository.deleteAll();
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    ownerAToken = seedStoreWithOwner(A, "owner-a@perm.test");
    ownerBToken = seedStoreWithOwner(B, "owner-b@perm.test");

    String created =
        mvc.perform(
                post("/api/m/" + A + "/admin/team")
                    .header("Authorization", "Bearer " + ownerAToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"staff-a@perm.test","password":"Staff@123456","displayName":"Ayanda",
                         "payMethod":"PER_SERVICE","payRate":100}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    staffEmployeeId = objectMapper.readTree(created).path("member").path("id").asText();
    staffToken = login("staff-a@perm.test", "Staff@123456");
  }

  private String seedStoreWithOwner(String slug, String email) throws Exception {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = slug;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    subscriptions.forceActivatePlan(t.id, TenantEntity.SubscriptionPlan.STANDARD);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = email;
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
    return login(email, "Owner@123456");
  }

  private String login(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  private void setStaffPermissions(List<String> perms) throws Exception {
    mvc.perform(
            put("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("permissions", perms))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usingDefault").value(false));
  }

  private static final List<String> EVERYTHING =
      List.of(
          "ORDERS", "BOOKINGS", "PAYMENTS", "PRODUCTS", "SERVICES", "PROMOTIONS", "TEAM_PAYROLL",
          "STORE_SETTINGS", "INSIGHTS", "CONFIRM_PAYMENTS", "DELETE_RECORDS", "CANCEL_BOOKINGS");

  @Test
  void newStaffKeepTodaysAccessUntilTheOwnerChangesIt() throws Exception {
    mvc.perform(get("/api/m/" + A + "/admin/access/me").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.permissions.length()").value(8))
        .andExpect(jsonPath("$.permissions[0]").value("ORDERS"));
    mvc.perform(
            get("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerAToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usingDefault").value(true));

    // Today's staff access: orders yes, products / store settings / payroll no.
    mvc.perform(get("/api/m/" + A + "/admin/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/m/" + A + "/admin/orders/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/m/" + A + "/admin/team/payment-calculations").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    mvc.perform(
            put("/api/m/" + A + "/admin/store/contact")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"X\"}"))
        .andExpect(status().isForbidden());
    // Always allowed to staff: notifications, help, own income, reading settings.
    mvc.perform(get("/api/m/" + A + "/admin/notifications").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(get("/api/m/" + A + "/admin/help/contact").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(get("/api/m/" + A + "/admin/store/settings").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(get("/api/m/" + A + "/admin/team/my-expected-income").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
  }

  @Test
  void ownerHasEverything() throws Exception {
    mvc.perform(get("/api/m/" + A + "/admin/access/me").header("Authorization", "Bearer " + ownerAToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isOwner").value(true))
        .andExpect(jsonPath("$.permissions.length()").value(12));
  }

  @Test
  void staffWithoutOrdersIsForbiddenOnOrders_changeAppliesToTheSameSession() throws Exception {
    setStaffPermissions(List.of("BOOKINGS"));
    mvc.perform(get("/api/m/" + A + "/admin/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));

    setStaffPermissions(List.of("INSIGHTS"));
    // Insights reads the order list, so it may load it — but cannot act on orders.
    mvc.perform(get("/api/m/" + A + "/admin/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/m/" + A + "/admin/orders/" + UUID.randomUUID() + "/fulfillment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"processing\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffWithOrdersButNotDeleteRecordsCannotDeleteOrConfirm() throws Exception {
    setStaffPermissions(List.of("ORDERS", "PRODUCTS"));
    mvc.perform(get("/api/m/" + A + "/admin/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/m/" + A + "/admin/orders/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    mvc.perform(
            delete("/api/m/" + A + "/admin/products/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    mvc.perform(
            post("/api/m/" + A + "/admin/orders/" + UUID.randomUUID() + "/confirm-payment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashCode\":\"123456\"}"))
        .andExpect(status().isForbidden());
    // PRODUCTS was granted, so a product write gets past the permission check.
    mvc.perform(
            post("/api/m/" + A + "/admin/products")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Soap\",\"price\":\"10\",\"stock\":\"1\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void onlyTheOwnerCanChangeBankingStaffOrPermissions() throws Exception {
    setStaffPermissions(EVERYTHING);
    String banking = "{\"bankName\":\"Bank\",\"bankAccountNumber\":\"123\"}";
    mvc.perform(
            put("/api/m/" + A + "/admin/store/banking")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(banking))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    // STORE_SETTINGS still lets them change non-banking settings.
    mvc.perform(
            put("/api/m/" + A + "/admin/store/contact")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"X\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"ORDERS\"]}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/m/" + A + "/admin/team")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@perm.test\",\"password\":\"Staff@123456\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/m/" + A + "/admin/team/" + staffEmployeeId).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/m/" + A + "/admin/subscription/plan")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tier\":\"STANDARD\"}"))
        .andExpect(status().isForbidden());
    // TEAM_PAYROLL does give payroll views.
    mvc.perform(
            get("/api/m/" + A + "/admin/team/payment-calculations").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());

    mvc.perform(
            put("/api/m/" + A + "/admin/store/banking")
                .header("Authorization", "Bearer " + ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(banking))
        .andExpect(status().isOk());
  }

  @Test
  void staffOfOneStoreCannotTouchAnother() throws Exception {
    setStaffPermissions(EVERYTHING);
    mvc.perform(get("/api/m/" + B + "/admin/orders").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
    mvc.perform(get("/api/m/" + B + "/admin/access/me").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/m/" + B + "/admin/store/contact")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"Hijack\"}"))
        .andExpect(status().isForbidden());
    // Store B's owner cannot edit store A's staff through store B.
    mvc.perform(
            put("/api/m/" + B + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("employee_not_found"));
  }

  @Test
  void ownerCanClearAllAccessResetToDefaultAndIsValidated() throws Exception {
    setStaffPermissions(List.of());
    mvc.perform(get("/api/m/" + A + "/admin/access/me").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(0));
    mvc.perform(get("/api/m/" + A + "/admin/notifications").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk());

    mvc.perform(
            put("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"ORDERS\",\"MAKE_COFFEE\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_permission"));

    mvc.perform(
            put("/api/m/" + A + "/admin/team/" + staffEmployeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetToDefault\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usingDefault").value(true))
        .andExpect(jsonPath("$.permissions.length()").value(8));
    mvc.perform(get("/api/m/" + A + "/admin/team").header("Authorization", "Bearer " + ownerAToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team[0].permissionsDefault").value(true));
  }
}
