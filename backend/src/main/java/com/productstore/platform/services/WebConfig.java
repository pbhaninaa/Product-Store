package com.productstore.platform.services;

import com.productstore.platform.services.multitenancy.PathBasedTenantResolver;
import com.productstore.platform.services.multitenancy.TenantContextFilter;
import com.productstore.platform.services.multitenancy.TenantResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfig {
  private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

  @Bean
  TenantResolver tenantResolver() {
    return new PathBasedTenantResolver();
  }

  @Bean
  FilterRegistrationBean<TenantContextFilter> tenantContextFilter(TenantResolver resolver) {
    FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>();
    reg.setFilter(new TenantContextFilter(resolver));
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    reg.addUrlPatterns("/api/*");
    return reg;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(Environment env) {
    CorsConfiguration cfg = new CorsConfiguration();
    if (isPermissiveCors(env)) {
      cfg.setAllowedOriginPatterns(List.of("*"));
    } else {
      List<String> origins = configuredOrigins(env);
      if (origins.isEmpty()) {
        // Keep booting so Railway healthchecks pass, but never fall back to allow-all on a
        // deployed environment: browsers are refused until the frontend origin is configured.
        log.error(
            "CORS has no allowed origins for profiles {}. Set {} or PUBLIC_APP_BASE_URL to the"
                + " frontend URL; browser calls from the web app will be refused until then.",
            Arrays.toString(env.getActiveProfiles()),
            activeProfiles(env).contains("prod") ? "PROD_CORS_ORIGINS" : "UAT_CORS_ORIGINS");
        cfg.setAllowedOrigins(List.of());
      } else {
        cfg.setAllowedOrigins(origins);
      }
    }
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  private static List<String> activeProfiles(Environment env) {
    return Arrays.stream(env.getActiveProfiles()).map(String::toLowerCase).toList();
  }

  /** Local/SIT/test/dev (and no profile): allow any origin. UAT/PROD: only configured origins. */
  public static boolean isPermissiveCors(Environment env) {
    List<String> profiles = activeProfiles(env);
    return !profiles.contains("prod")
        && !profiles.contains("uat")
        && (profiles.contains("local")
            || profiles.contains("sit")
            || profiles.contains("test")
            || profiles.contains("dev")
            || profiles.isEmpty());
  }

  /** Frontend origins configured for UAT/PROD (may be empty when misconfigured). */
  public static List<String> configuredOrigins(Environment env) {
    Set<String> origins = new LinkedHashSet<>();
    addOrigins(origins, env.getProperty("PROD_CORS_ORIGINS"));
    addOrigins(origins, env.getProperty("UAT_CORS_ORIGINS"));
    addOrigins(origins, env.getProperty("app.cors.allowed-origins"));
    addOrigins(origins, env.getProperty("PUBLIC_APP_BASE_URL"));
    return new ArrayList<>(origins);
  }

  /**
   * Origin patterns for non-HTTP-CORS checks (e.g. the WebSocket/SockJS handshake), matching the
   * HTTP CORS allowlist. An empty list means same-origin only.
   */
  public static List<String> allowedOriginPatterns(Environment env) {
    return isPermissiveCors(env) ? List.of("*") : configuredOrigins(env);
  }

  private static void addOrigins(Set<String> destinations, String raw) {
    if (!StringUtils.hasText(raw)) {
      return;
    }
    for (String part : raw.split(",")) {
      String origin = part.trim().replaceAll("/+$", "");
      if (!origin.isEmpty()) {
        destinations.add(origin);
      }
    }
  }
}
