package com.productstore.platform.services.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.productstore.platform.entities.UserEntity;
import org.junit.jupiter.api.Test;

class ShadowPathRulesTest {

  @Test
  void shadowCannotRefreshChangePasswordBankingOrTeamWrites() {
    assertThat(JwtAuthFilter.isShadowBlocked("POST", "/api/auth/refresh")).isTrue();
    assertThat(JwtAuthFilter.isShadowBlocked("POST", "/api/auth/change-password")).isTrue();
    assertThat(JwtAuthFilter.isShadowBlocked("PUT", "/api/m/shop/admin/store/banking")).isTrue();
    assertThat(JwtAuthFilter.isShadowBlocked("POST", "/api/m/shop/admin/team")).isTrue();
    assertThat(JwtAuthFilter.isShadowBlocked("DELETE", "/api/m/shop/admin/team/123")).isTrue();
  }

  @Test
  void shadowCanStillReadAndDoNormalSupportWork() {
    assertThat(JwtAuthFilter.isShadowBlocked("GET", "/api/m/shop/admin/team")).isFalse();
    assertThat(JwtAuthFilter.isShadowBlocked("GET", "/api/m/shop/admin/store/settings")).isFalse();
    assertThat(JwtAuthFilter.isShadowBlocked("PUT", "/api/m/shop/admin/store/contact")).isFalse();
    assertThat(JwtAuthFilter.isShadowBlocked("POST", "/api/m/shop/admin/orders/1/confirm-payment")).isFalse();
  }

  @Test
  void tokensIssuedBeforeRevocationSecondAreRejected() {
    UserEntity u = new UserEntity();
    assertThat(u.acceptsTokenIssuedAt(Instant.parse("2020-01-01T00:00:00Z"))).isTrue();
    u.tokensValidAfter = Instant.parse("2026-11-19T10:00:00.700Z");
    assertThat(u.acceptsTokenIssuedAt(Instant.parse("2026-11-19T09:59:59Z"))).isFalse();
    assertThat(u.acceptsTokenIssuedAt(Instant.parse("2026-11-19T10:00:00Z"))).isTrue();
    assertThat(u.acceptsTokenIssuedAt(null)).isFalse();
  }
}
