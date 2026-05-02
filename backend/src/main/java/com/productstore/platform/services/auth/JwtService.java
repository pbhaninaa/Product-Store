package com.productstore.platform.services.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final Algorithm algorithm;
  private final String issuer;
  private final String audience;

  public JwtService(
      @Value("${app.auth.jwtSecret}") String jwtSecret,
      @Value("${app.auth.jwtIssuer}") String issuer,
      @Value("${app.auth.jwtAudience}") String audience) {
    this.algorithm = Algorithm.HMAC256(jwtSecret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.audience = audience;
  }

  /**
   * @param tenantId merchant tenant id (null for platform staff without a store context)
   * @param tenantSlug URL slug for path-based tenancy (null if none)
   */
  public String mintToken(UUID userId, String email, List<Role> roles, UUID tenantId, String tenantSlug) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(60L * 60L * 24L * 7L); // 7 days

    var builder =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(exp))
            .withClaim("email", email)
            .withClaim("roles", roles.stream().map(Enum::name).toList());

    if (tenantId != null) {
      builder.withClaim("tenantId", tenantId.toString());
    }
    if (tenantSlug != null && !tenantSlug.isBlank()) {
      builder.withClaim("tenant", tenantSlug.trim());
    }

    return builder.sign(algorithm);
  }

  public DecodedJWT verify(String token) {
    return JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .acceptIssuedAt(120)
        .acceptExpiresAt(120)
        .build()
        .verify(token);
  }
}

