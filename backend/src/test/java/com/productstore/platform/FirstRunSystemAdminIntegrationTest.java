package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * First-run rule: on an empty database the only way in is the system admin form, the first account
 * becomes PLATFORM_ADMIN, and client/provider sign-up opens only after that.
 */
@SpringBootTest(
    classes = PlatformApplication.class,
    // Own in-memory database so other test classes' users never leak in.
    properties = "spring.datasource.url=jdbc:h2:mem:firstrun;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirstRunSystemAdminIntegrationTest {
  private static final String PASSWORD = "Secret@123456";

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;

  @BeforeEach
  void emptyDatabase() {
    memberships.deleteAll();
    users.deleteAll();
  }

  @Test
  void emptyDatabaseRequiresSystemAdminBeforeClientOrProviderSignup() throws Exception {
    mvc.perform(get("/api/auth/setup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needsSystemAdmin").value(true));

    mvc.perform(post("/api/auth/register-client").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"early-client@test.local\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("setup_required"));

    mvc.perform(post("/api/auth/register-merchant").contentType(MediaType.APPLICATION_JSON)
            .content("{\"merchantName\":\"Early Shop\",\"ownerEmail\":\"early-owner@test.local\",\"ownerPassword\":\""
                + PASSWORD + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("setup_required"));

    mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"Root@Test.Local\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.roles[0]").value("PLATFORM_ADMIN"));

    var admin = users.findByEmailIgnoreCase("root@test.local").orElseThrow();
    assertThat(memberships.findAllByUserId(admin.id)).anyMatch(m -> m.role == Role.PLATFORM_ADMIN);

    mvc.perform(get("/api/auth/setup-status"))
        .andExpect(jsonPath("$.needsSystemAdmin").value(false));

    // A second system admin cannot be self-registered.
    mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"second@test.local\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("setup_complete"));

    mvc.perform(post("/api/auth/register-client").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"client@test.local\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("CLIENT"));
  }

  @Test
  void systemAdminPasswordMustBeAtLeastEightCharacters() throws Exception {
    mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"root@test.local\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("password_too_short"));
    assertThat(users.count()).isZero();
  }
}
