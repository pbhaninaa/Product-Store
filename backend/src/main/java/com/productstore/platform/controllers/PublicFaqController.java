package com.productstore.platform.controllers;

import java.util.Map;

import com.productstore.platform.services.FaqService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/faqs")
public class PublicFaqController {
  private final FaqService faqs;

  public PublicFaqController(FaqService faqs) {
    this.faqs = faqs;
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam(required = false) String audience) {
    return Map.of("sections", faqs.list(audience));
  }
}
