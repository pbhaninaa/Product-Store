package com.productstore.platform.services;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class StaticUploadsConfig implements WebMvcConfigurer {
  @Value("${app.uploads.dir:./data/uploads}")
  private String uploadsDir;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path root = Paths.get(uploadsDir).toAbsolutePath().normalize();
    String location = "file:" + root + java.io.File.separator;
    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations(location)
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws java.io.IOException {
                // Never expose private proof storage that may live under uploads/_private.
                String p = resourcePath == null ? "" : resourcePath.replace('\\', '/');
                if (p.startsWith("_private/") || p.contains("/_private/")) {
                  return null;
                }
                return super.getResource(resourcePath, location);
              }
            });
  }
}
