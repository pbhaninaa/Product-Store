package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderReviewEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderReviewRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Regression tests for low-severity access-control findings. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LowSeverityHardeningIntegrationTest {
  private static final String PASSWORD = "Secret@123456";
  private static final byte[] PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired TenantRepository tenants;
  @Autowired OrderReviewRepository orderReviews;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  private record Merchant(String token, String slug) {}

  private Merchant registerMerchant() throws Exception {
    mvc.perform(
        post("/api/auth/register-system-admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", "lsh-admin-" + UUID.randomUUID() + "@test.local", "password", PASSWORD))));
    String body =
        json.writeValueAsString(
            Map.of(
                "merchantName", "LSH Shop " + UUID.randomUUID().toString().substring(0, 8),
                "ownerEmail", "lsh-" + UUID.randomUUID() + "@test.local",
                "ownerPassword", PASSWORD));
    JsonNode out =
        json.readTree(
            mvc.perform(
                    post("/api/auth/register-merchant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Merchant(out.get("token").asText(), out.get("merchantSlug").asText());
  }

  private String staffToken(Role role, String permissionsCsv) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = "lsh-staff-" + u.id + "@test.local";
    u.passwordHash = passwordHasher.hash("unused");
    u.createdAt = Instant.now();
    users.save(u);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.role = role;
    m.permissions = permissionsCsv;
    m.createdAt = Instant.now();
    memberships.save(m);
    return jwtService.mintToken(u.id, u.email, List.of(role), null, null);
  }

  private String staffBody(List<String> permissions) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "email", "lsh-new-" + UUID.randomUUID() + "@test.local",
            "password", PASSWORD,
            "permissions", permissions));
  }

  @Test
  void supportStaffCannotGrantPermissionsTheyDoNotHold() throws Exception {
    String limited = staffToken(Role.SUPPORT_USER, SupportPermission.MANAGE_STAFF.name());

    mvc.perform(
            post("/api/support/staff")
                .header("Authorization", "Bearer " + limited)
                .contentType(MediaType.APPLICATION_JSON)
                .content(staffBody(List.of("MANAGE_STAFF", "MANAGE_PLANS"))))
        .andExpect(status().isForbidden());

    // Empty request falls back to the defaults capped to the creator's own: nothing left here.
    mvc.perform(
            post("/api/support/staff")
                .header("Authorization", "Bearer " + limited)
                .contentType(MediaType.APPLICATION_JSON)
                .content(staffBody(List.of())))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/support/staff")
                .header("Authorization", "Bearer " + limited)
                .contentType(MediaType.APPLICATION_JSON)
                .content(staffBody(List.of("MANAGE_STAFF"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions[0]").value("MANAGE_STAFF"));

    String admin = staffToken(Role.PLATFORM_ADMIN, null);
    mvc.perform(
            post("/api/support/staff")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(staffBody(List.of("MANAGE_PLANS", "VIEW_AUDIT"))))
        .andExpect(status().isOk());
  }

  @Test
  void ratedLookupIsScopedToTheStoreInTheUrl() throws Exception {
    Merchant a = registerMerchant();
    Merchant b = registerMerchant();
    UUID tenantA = tenants.findBySlug(a.slug()).orElseThrow().id;

    OrderReviewEntity r = new OrderReviewEntity();
    r.id = UUID.randomUUID();
    r.tenantId = tenantA;
    r.orderId = UUID.randomUUID();
    r.customerEmail = "buyer@test.local";
    r.rating = 5;
    r.createdAt = Instant.now();
    orderReviews.save(r);

    mvc.perform(
            get("/api/public/m/" + a.slug() + "/reviews/rated")
                .param("kind", "order")
                .param("id", r.orderId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rated").value(true));
    mvc.perform(
            get("/api/public/m/" + b.slug() + "/reviews/rated")
                .param("kind", "order")
                .param("id", r.orderId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rated").value(false));
  }

  @Test
  void adminProductImageRequiresStoreMembership() throws Exception {
    Merchant owner = registerMerchant();
    Merchant other = registerMerchant();
    String id =
        json.readTree(
                mvc.perform(
                        multipart("/api/m/" + owner.slug() + "/admin/products")
                            .file(new MockMultipartFile("image", "p.png", "image/png", PNG))
                            .param("name", "Photo")
                            .param("category", "Test")
                            .param("price", "10")
                            .param("stock", "3")
                            .header("Authorization", "Bearer " + owner.token()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    String url = "/api/m/" + owner.slug() + "/admin/products/" + id + "/image";

    mvc.perform(get(url).header("Authorization", "Bearer " + owner.token()))
        .andExpect(status().isOk());
    mvc.perform(get(url).header("Authorization", "Bearer " + other.token()))
        .andExpect(status().isForbidden());
    mvc.perform(get(url)).andExpect(status().is4xxClientError());
  }

  @Test
  void retiredPeachRoutesAnswerGone() throws Exception {
    mvc.perform(get("/api/public/peach/configured")).andExpect(status().isGone());
    mvc.perform(
            post("/api/public/peach/webhook")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("result.code", "000.000.000"))
        .andExpect(status().isGone());
    mvc.perform(
            post("/api/public/peach/return")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("returnPath", "/m/x/peach/return"))
        .andExpect(status().isGone());
  }
}
