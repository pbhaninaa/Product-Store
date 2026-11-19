package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SitDangerZoneGuardTest {

  private static SitDangerZoneService service(String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return new SitDangerZoneService(
        env, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null);
  }

  @Test
  void wipeIsRefusedOnProdUatAndUnsetProfile() {
    ApiUserPrincipal admin =
        new ApiUserPrincipal(UUID.randomUUID(), "a@test.local", java.util.List.of(Role.PLATFORM_ADMIN), null);
    for (String[] profiles : new String[][] {{"prod"}, {"uat"}, {}}) {
      SitDangerZoneService s = service(profiles);
      assertThat(s.status().get("available")).isEqualTo(false);
      assertThatThrownBy(() -> s.wipeMerchantData(admin, SitDangerZoneService.CONFIRM_PHRASE))
          .hasMessage("danger_zone_disabled");
    }
  }

  @Test
  void wipeIsOfferedOnSit() {
    assertThat(service("sit").status().get("available")).isEqualTo(true);
  }
}
