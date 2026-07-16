package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantSignupAndAdminPasswordIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired PasswordHasher passwordHasher;

  @BeforeEach
  void clearDb() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    UserEntity admin = new UserEntity();
    admin.id = UUID.randomUUID();
    admin.email = "admin@test.local";
    admin.passwordHash = passwordHasher.hash("Admin@123456");
    admin.createdAt = Instant.now();
    userRepository.save(admin);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = admin.id;
    m.tenantId = null;
    m.role = Role.PLATFORM_ADMIN;
    m.createdAt = Instant.now();
    membershipRepository.save(m);
  }

  @Test
  void merchantsRegisterDirectlyAndAdminCanChangePassword() throws Exception {
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

    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"admin@test.local","password":"Admin@123456"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("PLATFORM_ADMIN"))
            .andReturn();

    String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

    mvc.perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Admin@123456","newPassword":"NewAdmin@123456"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"admin@test.local","password":"NewAdmin@123456"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("PLATFORM_ADMIN"));
  }
}
