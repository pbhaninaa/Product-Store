package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirstSignupPlatformAdminIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;

  @BeforeEach
  void clearDb() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  @Test
  void firstSignupBecomesPlatformAdminThenMerchantsCanRegister() throws Exception {
    mvc.perform(get("/api/auth/setup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needsPlatformAdmin").value(true));

    mvc.perform(
            post("/api/auth/register-merchant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "merchantName":"Too Early",
                      "merchantSlug":"too-early",
                      "ownerEmail":"early@test.local",
                      "ownerPassword":"Secret@123456"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("system admin")));

    mvc.perform(
            post("/api/auth/register-platform-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"admin@test.local","password":"Admin@123456"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.roles[0]").value("PLATFORM_ADMIN"));

    mvc.perform(get("/api/auth/setup-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needsPlatformAdmin").value(false));

    mvc.perform(
            post("/api/auth/register-platform-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"second-admin@test.local","password":"Admin@123456"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already exists")));

    mvc.perform(
            post("/api/auth/register-merchant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "merchantName":"Acme Shop",
                      "merchantSlug":"acme-shop",
                      "ownerEmail":"owner@test.local",
                      "ownerPassword":"Secret@123456"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.roles[0]").value("MERCHANT_OWNER"))
        .andExpect(jsonPath("$.merchantSlug").value("acme-shop"));
  }
}
