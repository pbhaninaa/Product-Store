package com.productstore.platform.services;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class FaqService {
  private static final Logger log = LoggerFactory.getLogger(FaqService.class);

  private final List<Map<String, Object>> sections;

  public FaqService(ObjectMapper objectMapper) {
    List<Map<String, Object>> loaded = List.of();
    try {
      ClassPathResource resource = new ClassPathResource("faqs/product-store-faqs.json");
      try (InputStream in = resource.getInputStream()) {
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        loaded = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
      }
    } catch (Exception e) {
      log.warn("Could not load FAQs: {}", e.getMessage());
      loaded = new ArrayList<>();
    }
    this.sections = List.copyOf(loaded);
  }

  public List<Map<String, Object>> list(String audience) {
    String want = audience == null ? "" : audience.trim().toLowerCase();
    if (want.isBlank() || want.equals("all")) return sections;
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> section : sections) {
      String a = String.valueOf(section.getOrDefault("audience", "public")).toLowerCase();
      if (a.equals("public") || a.equals(want)) out.add(section);
    }
    return out;
  }
}
