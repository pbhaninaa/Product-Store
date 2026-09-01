package com.productstore.platform.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/** Rewrites Railway {@code mysql://} URLs before DataSource auto-config runs. */
public class RailwayDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 10;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.acceptsProfiles(Profiles.of("test"))) {
      return;
    }
    String raw = RailwayDatasourceUrls.pickUrl(
        environment.getProperty("SPRING_DATASOURCE_URL"),
        environment.getProperty("spring.datasource.url"),
        environment.getProperty("MYSQL_URL"));
    if (raw == null) {
      return;
    }
    String normalized = RailwayDatasourceUrls.normalize(raw);
    if (normalized == null || normalized.isBlank()) {
      return;
    }

    Map<String, Object> map = new HashMap<>();
    map.put("spring.datasource.url", normalized);
    map.put("SPRING_DATASOURCE_URL", normalized);

    String username = firstNonBlank(
        environment.getProperty("SPRING_DATASOURCE_USERNAME"),
        environment.getProperty("MYSQLUSER"),
        RailwayDatasourceUrls.extractUser(normalized));
    String password = firstNonBlank(
        environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
        environment.getProperty("MYSQLPASSWORD"),
        RailwayDatasourceUrls.extractPassword(normalized));
    boolean preferredRailwayOverLocal =
        RailwayDatasourceUrls.shouldPreferRailwayMysql(
            environment.getProperty("spring.datasource.url"), environment.getProperty("MYSQL_URL"));
    if (preferredRailwayOverLocal || isBlank(environment.getProperty("spring.datasource.username"))) {
      if (username != null) {
        map.put("spring.datasource.username", username);
      }
      if (password != null) {
        map.put("spring.datasource.password", password);
      }
    }

    if (map.isEmpty()) {
      return;
    }
    System.out.println(
        "[railway-datasource] jdbc host="
            + RailwayDatasourceUrls.extractHost(normalized)
            + " user="
            + (username == null ? "" : username));
    environment.getPropertySources().addFirst(new MapPropertySource("railwayDatasourceUrl", map));
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return null;
  }
}
