package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.JwtService;
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

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportConsoleApiIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  private String supportToken() {
    UserEntity sup = new UserEntity();
    sup.id = UUID.randomUUID();
    sup.email = "support-api@test.local";
    sup.passwordHash = passwordHasher.hash("unused");
    sup.createdAt = Instant.now();
    userRepository.save(sup);

    MembershipEntity ms = new MembershipEntity();
    ms.id = UUID.randomUUID();
    ms.userId = sup.id;
    ms.tenantId = null;
    ms.role = Role.SUPPORT_USER;
    ms.createdAt = Instant.now();
    membershipRepository.save(ms);

    return jwtService.mintToken(sup.id, sup.email, List.of(Role.SUPPORT_USER), null, null);
  }

  @BeforeEach
  void cleanUsersExceptTenants() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  @Test
  void merchantsListOkWithSupportJwt() throws Exception {
    String token = supportToken();
    mvc.perform(get("/api/support/merchants").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void createUpdateDeleteMerchant() throws Exception {
    String token = supportToken();
    String slug = "crud" + Long.toHexString(System.nanoTime());
    String email = slug + "@merchant-crud.test";

    mvc.perform(
            post("/api/support/merchants")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"CRUD Merchant\",\"slug\":\""
                        + slug
                        + "\",\"ownerEmail\":\""
                        + email
                        + "\",\"ownerPassword\":\"Test@12345678\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.merchant.slug").value(slug));

    mvc.perform(
            put("/api/support/merchants/" + slug)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed Store\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.merchant.name").value("Renamed Store"));

    mvc.perform(
            delete("/api/support/merchants/" + slug).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }
}
