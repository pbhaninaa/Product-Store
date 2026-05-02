package com.productstore.platform.services;

import com.productstore.platform.services.auth.JwtAuthFilter;
import com.productstore.platform.services.auth.JwtService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write("{\"error\":\"access_denied\",\"reason\":\"forbidden_api\"}");
                        }))
        .authorizeHttpRequests(
            auth ->
                auth
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/favicon.ico", "/robots.txt").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/uploads/**").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/platform-admin/**").hasRole("PLATFORM_ADMIN")
                    // Exact path + subtree (some matchers treat these differently).
                    .requestMatchers("/api/support", "/api/support/**")
                        .hasAnyRole("SUPPORT_USER", "PLATFORM_ADMIN")
                    // Merchant admin APIs: any authenticated principal (tenant checks in controllers).
                    .requestMatchers("/api/m/*/admin/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())
        .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}

