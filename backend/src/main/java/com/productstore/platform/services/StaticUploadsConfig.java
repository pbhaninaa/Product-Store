package com.productstore.platform.services;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticUploadsConfig implements WebMvcConfigurer {
  @Value("${app.uploads.dir:./data/uploads}")
  private String uploadsDir;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path root = Paths.get(uploadsDir).toAbsolutePath().normalize();
    String location = "file:" + root + java.io.File.separator;
    registry.addResourceHandler("/uploads/**").addResourceLocations(location);
  }
}
