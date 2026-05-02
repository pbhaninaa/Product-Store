package com.productstore.platform.services.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.auth0.jwt.interfaces.DecodedJWT;

@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
class JwtAuthFilterParseRolesTest {

  @Autowired JwtService jwtService;

  @Test
  void parsesRolesArrayFromMintedJwt() {
    String token =
        jwtService.mintToken(
            UUID.randomUUID(),
            "support-parse@test.local",
            List.of(Role.SUPPORT_USER, Role.PLATFORM_ADMIN),
            null,
            null);
    DecodedJWT jwt = jwtService.verify(token);
    assertThat(JwtAuthFilter.parseRoles(jwt))
        .containsExactly(Role.SUPPORT_USER, Role.PLATFORM_ADMIN);
  }

  @Test
  void parsesSingleMerchantRole() {
    UUID uid = UUID.randomUUID();
    UUID tid = UUID.randomUUID();
    String token =
        jwtService.mintToken(uid, "m@test.local", List.of(Role.MERCHANT_OWNER), tid, "demo-shop");
    DecodedJWT jwt = jwtService.verify(token);
    assertThat(JwtAuthFilter.parseRoles(jwt)).containsExactly(Role.MERCHANT_OWNER);
  }
}
