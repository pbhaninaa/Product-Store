package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantSlugUtilTest {

  @Test
  void normalizesSpacesAndUppercase() {
    assertThat(TenantSlugUtil.normalize("  My Store Name  ")).isEqualTo("my-store-name");
  }

  @Test
  void collapsesRepeatedHyphensAndTrimsEdges() {
    assertThat(TenantSlugUtil.normalize("a---b__c")).isEqualTo("a-b-c");
  }

  @Test
  void rejectsTooShort() {
    assertThatThrownBy(() -> TenantSlugUtil.normalize("x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid_slug");
  }

  @Test
  void rejectsTooLong() {
    assertThatThrownBy(() -> TenantSlugUtil.normalize("a".repeat(50)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid_slug");
  }
}
