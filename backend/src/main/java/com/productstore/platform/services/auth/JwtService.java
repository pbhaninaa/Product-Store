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
  private final long ttlSeconds;

  public JwtService(
      @Value("${app.auth.jwtSecret}") String jwtSecret,
      @Value("${app.auth.jwtIssuer}") String issuer,
      @Value("${app.auth.jwtAudience}") String audience,
      @Value("${app.auth.jwt-ttl-hours:12}") long jwtTtlHours) {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException("APP_JWT_SECRET (or JWT_SECRET) must be set");
    }
    if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      // HS256 secret shorter than the hash output can be brute-forced offline from any issued token.
      throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters");
    }
    this.algorithm = Algorithm.HMAC256(jwtSecret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.audience = audience;
    long hours = jwtTtlHours > 0 ? jwtTtlHours : 12L;
    this.ttlSeconds = hours * 3600L;
  }

  /**
   * @param tenantId merchant tenant id (null for platform staff without a store context)
   * @param tenantSlug URL slug for path-based tenancy (null if none)
   */
  public String mintToken(UUID userId, String email, List<Role> roles, UUID tenantId, String tenantSlug) {
    return mintToken(userId, email, roles, tenantId, tenantSlug, false);
  }

  public String mintToken(
      UUID userId,
      String email,
      List<Role> roles,
      UUID tenantId,
      String tenantSlug,
      boolean shadowSupport) {
    return mint(userId, email, roles, tenantId, tenantSlug, null, ttlSeconds);
  }

  /** Short-lived owner token for support; carries the support user's id so actions can be audited. */
  public String mintShadowToken(
      UUID ownerUserId, String ownerEmail, UUID tenantId, String tenantSlug, UUID supportActorId) {
    return mint(
        ownerUserId,
        ownerEmail,
        List.of(Role.MERCHANT_OWNER),
        tenantId,
        tenantSlug,
        supportActorId,
        SHADOW_TTL_SECONDS);
  }

  public static final long SHADOW_TTL_SECONDS = 3600L;

  private String mint(
      UUID userId,
      String email,
      List<Role> roles,
      UUID tenantId,
      String tenantSlug,
      UUID shadowActorId,
      long ttl) {
    boolean shadowSupport = shadowActorId != null;
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(ttl);

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
    if (shadowSupport) {
      builder.withClaim("shadowSupport", true);
      builder.withClaim("shadowActor", shadowActorId.toString());
    }

    return builder.sign(algorithm);
  }

  public DecodedJWT verify(String token) {
    return JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .acceptIssuedAt(120)
        .acceptExpiresAt(60)
        .build()
        .verify(token);
  }
}

