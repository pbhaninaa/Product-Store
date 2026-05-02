package com.productstore.platform.controllers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = "com.productstore.platform",
    exclude = {UserDetailsServiceAutoConfiguration.class})
@EntityScan(basePackages = "com.productstore.platform.entities")
@EnableJpaRepositories(basePackages = "com.productstore.platform.repositories")
public class PlatformApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlatformApplication.class, args);
  }
}

