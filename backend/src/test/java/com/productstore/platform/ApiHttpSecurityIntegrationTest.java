package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiHttpSecurityIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  @BeforeEach
  void seedDemoTenant() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "demo";
    t.name = "Demo";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = "owner@security-test.local";
    u.passwordHash = passwordHasher.hash("Test@123456");
    u.createdAt = Instant.now();
    userRepository.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = t.id;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    membershipRepository.save(m);
  }

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  @Test
  void publicCatalogIsPublic() throws Exception {
    mvc.perform(get("/api/public/m/demo/catalog")).andExpect(status().isOk());
  }

  @Test
  void merchantAdminWithoutJwtReturns401NotForbiddenDenyAll() throws Exception {
    mvc.perform(
            put("/api/m/demo/admin/store/branding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"x\",\"shopType\":\"normal_store\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void merchantAdminWithJwtCanUpdateBranding() throws Exception {
    UserEntity u = userRepository.findByEmailIgnoreCase("owner@security-test.local").orElseThrow();
    TenantEntity t = tenantRepository.findBySlug("demo").orElseThrow();
    String token =
        jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), t.id, t.slug);

    mvc.perform(
            put("/api/m/demo/admin/store/branding")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"Branded\",\"shopType\":\"normal_store\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void merchantAdminWithJwtCanSaveOpeningHoursViaPutAndPost() throws Exception {
    UserEntity u = userRepository.findByEmailIgnoreCase("owner@security-test.local").orElseThrow();
    TenantEntity t = tenantRepository.findBySlug("demo").orElseThrow();
    String token =
        jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), t.id, t.slug);

    String putPayload =
        "{\"openingHoursJson\":\"[{\\\"dayOfWeek\\\":1,\\\"start\\\":\\\"09:00\\\",\\\"end\\\":\\\"17:00\\\"},{\\\"dayOfWeek\\\":2,\\\"closed\\\":true}]\"}";

    mvc.perform(
            put("/api/m/demo/admin/store/opening-hours")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(putPayload))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/m/demo/admin/store/opening-hours")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingHoursJson\":\"[]\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void merchantAdminWithJwtCanCreateProductMultipart() throws Exception {
    UserEntity u = userRepository.findByEmailIgnoreCase("owner@security-test.local").orElseThrow();
    TenantEntity t = tenantRepository.findBySlug("demo").orElseThrow();
    String token =
        jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), t.id, t.slug);

    MockMultipartFile image =
        new MockMultipartFile("image", "t.png", "image/png", new byte[] {(byte) 0x89, (byte) 0x50});

    mvc.perform(
            multipart("/api/m/demo/admin/products")
                .file(image)
                .param("name", "Widget")
                .param("category", "Cat")
                .param("price", "12.50")
                .param("stock", "3")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void unknownApiPathWithoutJwtReturns401() throws Exception {
    mvc.perform(get("/api/not-a-real-controller/paths")).andExpect(status().isUnauthorized());
  }

  @Test
  void supportOverviewOkWhenJwtHasSupportRole() throws Exception {
    UserEntity sup = new UserEntity();
    sup.id = UUID.randomUUID();
    sup.email = "support@security-test.local";
    sup.passwordHash = passwordHasher.hash("Support@123456");
    sup.createdAt = Instant.now();
    userRepository.save(sup);

    MembershipEntity ms = new MembershipEntity();
    ms.id = UUID.randomUUID();
    ms.userId = sup.id;
    ms.tenantId = null;
    ms.role = Role.SUPPORT_USER;
    ms.createdAt = Instant.now();
    membershipRepository.save(ms);

    String token = jwtService.mintToken(sup.id, sup.email, List.of(Role.SUPPORT_USER), null, null);
    mvc.perform(get("/api/support/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void supportOverviewForbiddenForMerchantJwt() throws Exception {
    UserEntity u = userRepository.findByEmailIgnoreCase("owner@security-test.local").orElseThrow();
    TenantEntity t = tenantRepository.findBySlug("demo").orElseThrow();
    String token =
        jwtService.mintToken(u.id, u.email, List.of(Role.MERCHANT_OWNER), t.id, t.slug);
    mvc.perform(get("/api/support/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }
}
