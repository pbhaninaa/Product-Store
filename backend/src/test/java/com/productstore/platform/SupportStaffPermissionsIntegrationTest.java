package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Support permissions are editable only by platform admins; shadow sessions need USE_SHADOW. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportStaffPermissionsIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired UserRepository userRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired TenantRepository tenantRepository;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  @BeforeEach
  void clean() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();
  }

  private UserEntity user(String email) {
    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = email;
    u.passwordHash = passwordHasher.hash("unused-password");
    u.createdAt = Instant.now();
    return userRepository.save(u);
  }

  private MembershipEntity membership(UserEntity u, Role role, String permissionsCsv) {
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = null;
    m.role = role;
    m.permissions = permissionsCsv;
    m.createdAt = Instant.now();
    return membershipRepository.save(m);
  }

  private String token(UserEntity u, Role role) {
    return jwtService.mintToken(u.id, u.email, List.of(role), null, null);
  }

  private String storedPermissions(UUID userId) {
    return membershipRepository.findAllByUserId(userId).stream()
        .filter(m -> m.role == Role.SUPPORT_USER)
        .findFirst()
        .map(m -> m.permissions)
        .orElseThrow();
  }

  @Test
  void supportUserWithManageStaffCannotChangePermissions() throws Exception {
    UserEntity staffManager = user("manager@support.test");
    membership(staffManager, Role.SUPPORT_USER, "MANAGE_STAFF,VIEW_OPS");
    UserEntity target = user("target@support.test");
    membership(target, Role.SUPPORT_USER, "VIEW_OPS");

    mvc.perform(
            put("/api/support/staff/" + target.id + "/permissions")
                .header("Authorization", "Bearer " + token(staffManager, Role.SUPPORT_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"MANAGE_STAFF\",\"USE_SHADOW\"]}"))
        .andExpect(status().isForbidden());

    // A support user cannot escalate their own permissions either.
    mvc.perform(
            put("/api/support/staff/" + staffManager.id + "/permissions")
                .header("Authorization", "Bearer " + token(staffManager, Role.SUPPORT_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"MANAGE_STAFF\",\"MANAGE_FEATURES\"]}"))
        .andExpect(status().isForbidden());

    assertThat(storedPermissions(target.id)).isEqualTo("VIEW_OPS");
    assertThat(storedPermissions(staffManager.id)).isEqualTo("MANAGE_STAFF,VIEW_OPS");
  }

  @Test
  void platformAdminCanChangePermissions() throws Exception {
    UserEntity admin = user("admin@support.test");
    membership(admin, Role.PLATFORM_ADMIN, null);
    UserEntity target = user("target@support.test");
    membership(target, Role.SUPPORT_USER, "VIEW_OPS");

    mvc.perform(
            put("/api/support/staff/" + target.id + "/permissions")
                .header("Authorization", "Bearer " + token(admin, Role.PLATFORM_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[\"VIEW_AUDIT\",\"USE_SHADOW\",\"NOT_A_PERMISSION\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2))
        .andExpect(jsonPath("$.permissions[0]").value("USE_SHADOW"))
        .andExpect(jsonPath("$.permissions[1]").value("VIEW_AUDIT"));

    assertThat(storedPermissions(target.id)).isEqualTo("VIEW_AUDIT,USE_SHADOW");
  }

  @Test
  void emptyPermissionSetIsRejectedInsteadOfSilentlyResettingToDefaults() throws Exception {
    UserEntity admin = user("admin@support.test");
    membership(admin, Role.PLATFORM_ADMIN, null);
    UserEntity target = user("target@support.test");
    membership(target, Role.SUPPORT_USER, "VIEW_AUDIT");

    mvc.perform(
            put("/api/support/staff/" + target.id + "/permissions")
                .header("Authorization", "Bearer " + token(admin, Role.PLATFORM_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("permissions_required"));

    assertThat(storedPermissions(target.id)).isEqualTo("VIEW_AUDIT");
  }

  @Test
  void shadowTokenRequiresUseShadowPermission() throws Exception {
    UserEntity agent = user("agent@support.test");
    membership(agent, Role.SUPPORT_USER, "VIEW_OPS");

    mvc.perform(
            post("/api/support/shadow/token")
                .header("Authorization", "Bearer " + token(agent, Role.SUPPORT_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"any-store\"}"))
        .andExpect(status().isForbidden());
  }
}
