package com.productstore.platform.services.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
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

    tokenOpt.ifPresent(
        token -> {
          try {
            DecodedJWT jwt = jwtService.verify(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaim("email").asString();
            if (email == null) {
              email = "";
            }
            List<Role> roles = parseRoles(jwt);
            Claim tenantClaim = jwt.getClaim("tenant");
            String tenant =
                tenantClaim == null || tenantClaim.isNull() ? null : tenantClaim.asString();
            ApiUserPrincipal principal = new ApiUserPrincipal(userId, email, roles, tenant);

            var auth =
                new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
          } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
          }
        });

    filterChain.doFilter(request, response);
  }
}

