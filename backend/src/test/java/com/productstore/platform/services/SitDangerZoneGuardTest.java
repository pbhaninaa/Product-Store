package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SitDangerZoneGuardTest {

  /**
   * Repositories the reset deliberately keeps (CTO 2026-11-23): accounts and profiles, store set-up and
   * platform configuration. Everything else is activity and must be wiped by the reset.
   */
  private static final Set<String> KEPT_ON_RESET =
      Set.of(
          // accounts and profiles
          "UserRepository",
          "MembershipRepository",
          "TenantRepository",
          "ShopSettingsRepository",
          "EmployeeRepository",
          "SalonStaffRepository",
          "SalonStaffAvailabilityRepository",
          // store set-up
          "ProductRepository",
          "SalonServiceRepository",
          "PromotionRepository",
          "MerchantSubscriptionRepository",
          "ReferralRepository",
          // platform configuration
          "PlatformBankingRepository",
          "PlatformFeatureRepository",
          "PlatformHelpContactRepository",
          "SubscriptionPlanPricingRepository");

  @Test
  void resetNeverDeletesAccountsOrProfiles() {
    Set<String> wiped =
        Arrays.stream(SitDangerZoneService.class.getConstructors()[0].getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.toSet());
    assertThat(wiped).doesNotContainAnyElementsOf(KEPT_ON_RESET);
  }

  private static SitDangerZoneService service(String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    Constructor<?> ctor = SitDangerZoneService.class.getConstructors()[0];
    Object[] args = new Object[ctor.getParameterCount()];
    args[0] = env;
    try {
      return (SitDangerZoneService) ctor.newInstance(args);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void wipeIsRefusedOnProdUatAndUnsetProfile() {
    ApiUserPrincipal admin =
        new ApiUserPrincipal(UUID.randomUUID(), "a@test.local", java.util.List.of(Role.PLATFORM_ADMIN), null);
    for (String[] profiles : new String[][] {{"prod"}, {"uat"}, {}}) {
      SitDangerZoneService s = service(profiles);
      assertThat(s.status().get("available")).isEqualTo(false);
      assertThatThrownBy(() -> s.resetRecords(admin, SitDangerZoneService.CONFIRM_PHRASE))
          .hasMessage("danger_zone_disabled");
    }
  }

  @Test
  void wipeIsOfferedOnSit() {
    assertThat(service("sit").status().get("available")).isEqualTo(true);
  }

  /** A new table must be wiped by the reset or explicitly listed as kept; otherwise it leaves orphans. */
  @Test
  void resetCoversEveryRepository() {
    File dir = new File("src/main/java/com/productstore/platform/repositories");
    Set<String> all =
        Arrays.stream(dir.listFiles((d, n) -> n.endsWith("Repository.java")))
            .map(f -> f.getName().replace(".java", ""))
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> wiped =
        Arrays.stream(SitDangerZoneService.class.getConstructors()[0].getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.toSet());

    Set<String> missing = new TreeSet<>(all);
    missing.removeAll(wiped);
    missing.removeAll(KEPT_ON_RESET);
    assertThat(missing).as("repositories not handled by the database reset").isEmpty();
  }
}
