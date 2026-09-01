package com.productstore.platform.services;

import com.productstore.platform.services.multitenancy.PathBasedTenantResolver;
import com.productstore.platform.services.multitenancy.TenantContextFilter;
import com.productstore.platform.services.multitenancy.TenantResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    List<String> profiles =
        Arrays.stream(env.getActiveProfiles()).map(String::toLowerCase).toList();
    boolean prod = profiles.contains("prod");
    boolean uat = profiles.contains("uat");
    // Local/SIT/test: allow any origin. UAT/PROD: MarketPlace-style CORS env vars.
    boolean permissiveCors =
        !prod
            && !uat
            && (profiles.contains("local")
                || profiles.contains("sit")
                || profiles.contains("test")
                || profiles.contains("dev")
                || profiles.isEmpty());
    if (permissiveCors) {
      cfg.setAllowedOriginPatterns(List.of("*"));
    } else {
      Set<String> origins = new LinkedHashSet<>();
      addOrigins(origins, env.getProperty("PROD_CORS_ORIGINS"));
      addOrigins(origins, env.getProperty("UAT_CORS_ORIGINS"));
      addOrigins(origins, env.getProperty("app.cors.allowed-origins"));
      addOrigins(origins, env.getProperty("PUBLIC_APP_BASE_URL"));
      if (origins.isEmpty()) {
        // Missing CORS must not fail Railway boot / healthchecks after a DB rebuild.
        // Tighten this once PROD_CORS_ORIGINS / UAT_CORS_ORIGINS are set.
        cfg.setAllowedOriginPatterns(List.of("*"));
      } else {
        cfg.setAllowedOrigins(new ArrayList<>(origins));
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
