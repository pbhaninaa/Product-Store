package com.productstore.platform.services.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.SupportAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

  /** Owner actions support must never take while shadowing (money routing, staff, credentials). */
  private static final List<Pattern> SHADOW_BLOCKED =
      List.of(
          Pattern.compile("^/api/auth/(refresh|change-password)$"),
          Pattern.compile("^/api/m/[^/]+/admin/store/banking$"),
          Pattern.compile("^/api/m/[^/]+/admin/team(/.*)?$"));

  private final JwtService jwtService;
  private final UserRepository users;
  private final SupportAuditService audit;

  public JwtAuthFilter(JwtService jwtService, UserRepository users, SupportAuditService audit) {
    this.jwtService = jwtService;
    this.users = users;
    this.audit = audit;
  }

  static boolean isShadowBlocked(String method, String path) {
    if (path == null) return false;
    boolean isRead = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    if (isRead && !path.startsWith("/api/auth/")) return false;
    return SHADOW_BLOCKED.stream().anyMatch(p -> p.matcher(path).matches());
  }

  private static String requestPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String ctx = request.getContextPath();
    if (uri == null) return "";
    return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  static Optional<String> extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null) {
      return Optional.empty();
    }
    String h = authorizationHeader.trim();
    int sp = h.indexOf(' ');
    if (sp < 0 || sp >= h.length() - 1) {
      return Optional.empty();
    }
    String scheme = h.substring(0, sp);
    if (!scheme.equalsIgnoreCase("Bearer")) {
      return Optional.empty();
    }
    String raw = h.substring(sp + 1).trim();
    return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
  }

  /** Maps JWT role strings to enums (case-insensitive, optional {@code ROLE_} prefix, comma lists). */
  static Role parseRoleToken(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.isEmpty()) {
      return null;
    }
    if (s.regionMatches(true, 0, "ROLE_", 0, 5)) {
      s = s.substring(5).trim();
    }
    if (s.isEmpty()) {
      return null;
    }
    for (Role r : Role.values()) {
      if (r.name().equalsIgnoreCase(s)) {
        return r;
      }
    }
    return null;
  }

  static List<Role> parseRoles(DecodedJWT jwt) {
    Claim claim = jwt.getClaim("roles");
    if (claim == null || claim.isNull()) {
      return List.of();
    }
    try {
      // Prefer typed list decode — claim.as(Object.class) often fails to map roles for
      // AuthorizationManager (hasAnyRole) leaving principals with no authorities → 403 on /api/support/**.
      List<String> asStrings = null;
      try {
        asStrings = claim.asList(String.class);
      } catch (RuntimeException ignored) {
        // fall through to Object parsing
      }
      if (asStrings != null && !asStrings.isEmpty()) {
        List<Role> roles = new ArrayList<>();
        for (String s : asStrings) {
          Role r = parseRoleToken(s);
          if (r != null) {
            roles.add(r);
          }
        }
        return List.copyOf(roles);
      }

      Object raw = claim.as(Object.class);
      if (raw == null) {
        return List.of();
      }
      if (raw instanceof List<?> list) {
        List<Role> roles = new ArrayList<>();
        for (Object o : list) {
          if (o == null) {
            continue;
          }
          Role r = parseRoleToken(String.valueOf(o));
          if (r != null) {
            roles.add(r);
          }
        }
        return List.copyOf(roles);
      }
      String single = raw instanceof String s ? s : String.valueOf(raw);
      single = single.trim();
      if (single.isEmpty()) {
        return List.of();
      }
      if (single.indexOf(',') >= 0) {
        List<Role> roles = new ArrayList<>();
        for (String part : single.split(",")) {
          Role r = parseRoleToken(part);
          if (r != null) {
            roles.add(r);
          }
        }
        return List.copyOf(roles);
      }
      Role one = parseRoleToken(single);
      return one == null ? List.of() : List.of(one);
    } catch (RuntimeException e) {
      return List.of();
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<String> tokenOpt =
        Optional.ofNullable(request.getHeader("Authorization")).flatMap(JwtAuthFilter::extractBearerToken);

    ApiUserPrincipal authenticated = null;
    UUID shadowActor = null;
    if (tokenOpt.isPresent()) {
      String token = tokenOpt.get();
          try {
            DecodedJWT jwt = jwtService.verify(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            // Tokens are only as good as the account behind them: suspended users and tokens issued
            // before a password change / sign-out-everywhere are rejected on every request.
            UserEntity user = users.findById(userId).orElse(null);
            Instant issuedAt = jwt.getIssuedAt() == null ? null : jwt.getIssuedAt().toInstant();
            if (user == null || user.suspended || !user.acceptsTokenIssuedAt(issuedAt)) {
              throw new IllegalStateException("token_revoked");
            }
            String email = jwt.getClaim("email").asString();
            if (email == null) {
              email = "";
            }
            List<Role> roles = parseRoles(jwt);
            Claim tenantClaim = jwt.getClaim("tenant");
            String tenant =
                tenantClaim == null || tenantClaim.isNull() ? null : tenantClaim.asString();
            Claim shadowClaim = jwt.getClaim("shadowSupport");
            boolean shadow =
                shadowClaim != null && !shadowClaim.isNull() && Boolean.TRUE.equals(shadowClaim.asBoolean());
            if (shadow) {
              Claim actorClaim = jwt.getClaim("shadowActor");
              String actorRaw = actorClaim == null || actorClaim.isNull() ? null : actorClaim.asString();
              // Shadow tokens minted before actor tracking can't be audited, so they are not honoured.
              if (actorRaw == null) throw new IllegalStateException("shadow_actor_missing");
              shadowActor = UUID.fromString(actorRaw);
            }
            ApiUserPrincipal principal = new ApiUserPrincipal(userId, email, roles, tenant, shadow);

            var auth =
                new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            authenticated = principal;
          } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
          }
    }

    if (authenticated != null && authenticated.shadowSupport()) {
      String path = requestPath(request);
      String method = request.getMethod();
      if (isShadowBlocked(method, path)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"shadow_forbidden\"}");
        return;
      }
      if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
          && !"OPTIONS".equalsIgnoreCase(method)) {
        try {
          ApiUserPrincipal actor =
              new ApiUserPrincipal(shadowActor, "shadow-support", List.of(Role.SUPPORT_USER), null);
          audit.record(
              actor,
              "SHADOW_ACTION",
              "TENANT",
              authenticated.tenantSlug(),
              method + " " + path + " as " + authenticated.getUsername());
        } catch (RuntimeException e) {
          // Fail closed: an unaudited support write must not go through.
          log.error("Could not audit shadow action {} {}", method, path, e);
          response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}

