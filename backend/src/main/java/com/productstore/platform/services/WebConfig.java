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
    List<String> profiles =
        Arrays.stream(env.getActiveProfiles()).map(String::toLowerCase).toList();
    boolean prod = profiles.contains("prod");
    boolean uat = profiles.contains("uat");
    // Local/SIT/test: allow any origin. UAT/PROD: only the configured frontend origins.
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
        // Keep booting so Railway healthchecks pass, but never fall back to allow-all on a
        // deployed environment: browsers are refused until the frontend origin is configured.
        log.error(
            "CORS has no allowed origins for profiles {}. Set {} or PUBLIC_APP_BASE_URL to the"
                + " frontend URL; browser calls from the web app will be refused until then.",
            profiles,
            prod ? "PROD_CORS_ORIGINS" : "UAT_CORS_ORIGINS");
        cfg.setAllowedOrigins(List.of());
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
