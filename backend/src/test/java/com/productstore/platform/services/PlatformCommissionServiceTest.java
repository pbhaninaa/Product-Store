package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PlatformCommissionServiceTest {

  @Test
  void percentagesFrom0To50WithTwoDecimalsBecomeBasisPoints() {
    assertThat(PlatformCommissionService.parseBasisPoints("12.5")).isEqualTo(1250);
    assertThat(PlatformCommissionService.parseBasisPoints(12.5)).isEqualTo(1250);
    assertThat(PlatformCommissionService.parseBasisPoints(" 7.25 ")).isEqualTo(725);
    assertThat(PlatformCommissionService.parseBasisPoints(10)).isEqualTo(1000);
    assertThat(PlatformCommissionService.parseBasisPoints("0")).isZero();
    assertThat(PlatformCommissionService.parseBasisPoints("50.00")).isEqualTo(5000);
    assertThat(PlatformCommissionService.parseBasisPoints(50)).isEqualTo(5000);
  }

  /** AC-4: out of range, not a number or more than two decimals. */
  @Test
  void anythingElseIsRejected() {
    for (Object bad : Arrays.asList(51, "50.01", -1, "-0.01", "abc", "", "12.345", 12.345, null, true)) {
      assertThatThrownBy(() -> PlatformCommissionService.parseBasisPoints(bad))
          .as(String.valueOf(bad))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("commission_invalid");
    }
  }

  @Test
  void basisPointsAreShownAsAPercentWithTwoDecimals() {
    assertThat(PlatformCommissionService.percent(1250)).isEqualTo("12.50");
    assertThat(PlatformCommissionService.percent(0)).isEqualTo("0.00");
    assertThat(PlatformCommissionService.percent(5)).isEqualTo("0.05");
    assertThat(PlatformCommissionService.percent(5000)).isEqualTo("50.00");
  }
}
