package com.productstore.platform.services;

import com.productstore.platform.services.multitenancy.PathBasedTenantResolver;
import com.productstore.platform.services.multitenancy.TenantContextFilter;
import com.productstore.platform.services.multitenancy.TenantResolver;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
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
    // Browsers send 403 on PUT/POST when Origin does not match; patterns like
    // "http://localhost:*" can still miss some cases (IPv6 literal, https dev, odd ports).
    // With allowCredentials=false, "*" is fine for every non-prod profile we run locally/UAT.
    boolean permissiveCors =
        !prod
            && (profiles.contains("local")
                || profiles.contains("test")
                || profiles.contains("dev")
                || profiles.contains("uat"));
    if (permissiveCors) {
      cfg.setAllowedOriginPatterns(List.of("*"));
    } else {
      cfg.setAllowedOriginPatterns(
          List.of(
              "http://localhost:*",
              "https://localhost:*",
              "http://127.0.0.1:*",
              "https://127.0.0.1:*",
              "http://[::1]:*",
              "https://[::1]:*",
              "http://192.168.*:*",
              "https://192.168.*:*",
              "http://10.*:*",
              "https://10.*:*"));
    }
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}

