package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.JwtService;
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

/** A person's weekly schedule can't contain two windows covering the same time on the same day. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalonAvailabilityOverlapIntegrationTest {

  private static final String SLUG = "overlap-salon";

  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired SalonStaffRepository staff;
  @Autowired SalonStaffAvailabilityRepository availability;
  @Autowired PasswordHasher passwordHasher;
  @Autowired JwtService jwtService;

  UUID tenantId;
  UUID bayanda;
  UUID thabo;
  String ownerToken;

  @BeforeEach
  void seed() {
    cleanup();
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Overlap Salon";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);
    tenantId = t.id;

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-overlap@test.local";
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
    ownerToken = jwtService.mintToken(owner.id, owner.email, List.of(Role.MERCHANT_OWNER), tenantId, SLUG);

    bayanda = staffMember("Bayanda");
    thabo = staffMember("Thabo");
  }

  @AfterEach
  void cleanup() {
    availability.deleteAll();
    staff.deleteAll();
    memberships.deleteAll();
    users.deleteAll();
    tenants.findBySlug(SLUG).ifPresent(tenants::delete);
  }

  @Test
  void overlappingOrRepeatedWindowsForTheSamePersonAndDayAreRefused() throws Exception {
    add(bayanda, 1, "09:00", "13:00").andExpect(status().isOk());

    add(bayanda, 1, "09:00", "13:00").andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("availability_overlap"));
    add(bayanda, 1, "12:00", "15:00").andExpect(status().isConflict());
    add(bayanda, 1, "08:00", "17:00").andExpect(status().isConflict());
    add(bayanda, 1, "10:00", "11:00").andExpect(status().isConflict());

    // Touching windows, another day, or another person are all fine.
    add(bayanda, 1, "13:00", "17:00").andExpect(status().isOk());
    add(bayanda, 2, "09:00", "13:00").andExpect(status().isOk());
    add(thabo, 1, "09:00", "13:00").andExpect(status().isOk());

    assertThat(availability.count()).isEqualTo(4);
  }

  @Test
  void badDaysAndBackwardsTimesAreRefused() throws Exception {
    add(bayanda, 0, "09:00", "10:00").andExpect(status().isBadRequest());
    add(bayanda, 8, "09:00", "10:00").andExpect(status().isBadRequest());
    add(bayanda, 1, "10:00", "09:00").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("availability_end_before_start"));
    add(bayanda, 1, "10:00", "10:00").andExpect(status().isBadRequest());
    add(bayanda, 1, "nine", "10:00").andExpect(status().isBadRequest());
    assertThat(availability.count()).isZero();
  }

  private ResultActions add(UUID staffId, int day, String from, String to) throws Exception {
    return mvc.perform(
        post("/api/m/" + SLUG + "/admin/salon/availability")
            .header("Authorization", "Bearer " + ownerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"staffId\":\"" + staffId + "\",\"dayOfWeek\":" + day + ",\"startTime\":\"" + from
                    + "\",\"endTime\":\"" + to + "\"}"));
  }

  private UUID staffMember(String name) {
    SalonStaffEntity s = new SalonStaffEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.displayName = name;
    s.active = true;
    s.createdAt = Instant.now();
    staff.save(s);
    return s.id;
  }
}
