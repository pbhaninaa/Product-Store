package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.services.SupportConsoleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportConsoleController {
  private final SupportConsoleService supportConsoleService;

  public SupportConsoleController(SupportConsoleService supportConsoleService) {
    this.supportConsoleService = supportConsoleService;
  }

  public record CreateMerchantRequest(
      @NotBlank String name,
      @NotBlank String slug,
      @Email @NotBlank String ownerEmail,
      @NotBlank String ownerPassword) {}

  public record UpdateMerchantRequest(String name, String slug) {}

  @GetMapping("/overview")
  public Map<String, Object> overview() {
    return supportConsoleService.overview();
  }

  @GetMapping("/merchants")
  public List<Map<String, Object>> merchants(@RequestParam(name = "q", required = false) String q) {
    return supportConsoleService.listMerchants(q);
  }

  @GetMapping("/merchants/{slug}")
  public Map<String, Object> merchantDetail(@PathVariable("slug") String slug) {
    return supportConsoleService.merchantDetail(slug);
  }

  @PostMapping("/merchants")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createMerchant(@Valid @RequestBody CreateMerchantRequest req) {
    return supportConsoleService.createMerchant(
        req.name(), req.slug(), req.ownerEmail(), req.ownerPassword());
  }

  @PutMapping("/merchants/{slug}")
  public Map<String, Object> updateMerchant(
      @PathVariable("slug") String slug, @Valid @RequestBody UpdateMerchantRequest req) {
    return supportConsoleService.updateMerchant(slug, req.name(), req.slug());
  }

  @DeleteMapping("/merchants/{slug}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMerchant(@PathVariable("slug") String slug) {
    supportConsoleService.deleteMerchant(slug);
  }
}
