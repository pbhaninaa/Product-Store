package com.productstore.platform.services.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import org.junit.jupiter.api.Test;

class JwtAuthFilterRoleTokenTest {

  @Test
  void parseRoleTokenHandlesCaseAndRolePrefix() {
    assertThat(JwtAuthFilter.parseRoleToken("MERCHANT_OWNER")).isEqualTo(Role.MERCHANT_OWNER);
    assertThat(JwtAuthFilter.parseRoleToken("merchant_owner")).isEqualTo(Role.MERCHANT_OWNER);
    assertThat(JwtAuthFilter.parseRoleToken("ROLE_MERCHANT_OWNER")).isEqualTo(Role.MERCHANT_OWNER);
    assertThat(JwtAuthFilter.parseRoleToken(" role_PLATFORM_ADMIN "))
        .isEqualTo(Role.PLATFORM_ADMIN);
    assertThat(JwtAuthFilter.parseRoleToken("nope")).isNull();
  }

  @Test
  void parseRolesFromDecodedJwtAcceptsLowercaseList() {
    byte[] secret =
        "test-secret-test-secret-test-secret-test-secret".getBytes(StandardCharsets.UTF_8);
    Algorithm alg = Algorithm.HMAC256(secret);
    String token =
        JWT.create()
            .withIssuer("test")
            .withAudience("test")
            .withSubject(UUID.randomUUID().toString())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
            .withClaim("email", "x@test")
            .withClaim("roles", List.of("support_user", "ROLE_PLATFORM_ADMIN"))
            .sign(alg);
    var jwt = JWT.require(alg).withIssuer("test").withAudience("test").build().verify(token);
    assertThat(JwtAuthFilter.parseRoles(jwt))
        .containsExactly(Role.SUPPORT_USER, Role.PLATFORM_ADMIN);
  }

  @Test
  void parseRolesSplitsCommaSeparatedStringClaim() {
    byte[] secret =
        "test-secret-test-secret-test-secret-test-secret".getBytes(StandardCharsets.UTF_8);
    Algorithm alg = Algorithm.HMAC256(secret);
    String token =
        JWT.create()
            .withIssuer("test")
            .withAudience("test")
            .withSubject(UUID.randomUUID().toString())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
            .withClaim("email", "x@test")
            .withClaim("roles", "MERCHANT_STAFF, MERCHANT_OWNER")
            .sign(alg);
    var jwt = JWT.require(alg).withIssuer("test").withAudience("test").build().verify(token);
    assertThat(JwtAuthFilter.parseRoles(jwt))
        .containsExactly(Role.MERCHANT_STAFF, Role.MERCHANT_OWNER);
  }
}
