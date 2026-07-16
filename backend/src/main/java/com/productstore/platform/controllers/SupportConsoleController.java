package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.constants.SupportPermission;
import com.productstore.platform.services.SupportAccessService;
import com.productstore.platform.services.SupportConsoleService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  private final SupportAccessService access;

  public SupportConsoleController(
      SupportConsoleService supportConsoleService, SupportAccessService access) {
    this.supportConsoleService = supportConsoleService;
    this.access = access;
  }

  public record CreateMerchantRequest(
      @NotBlank String name,
      @NotBlank String slug,
      @Email @NotBlank String ownerEmail,
      @NotBlank String ownerPassword,
      String subscriptionPlan) {}

  public record UpdateMerchantRequest(String name, String slug, String subscriptionPlan) {}

  @GetMapping("/overview")
  public Map<String, Object> overview(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requireSupport(principal);
    return supportConsoleService.overview();
  }

  @GetMapping("/merchants")
  public List<Map<String, Object>> merchants(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(name = "q", required = false) String q) {
    access.requirePermission(principal, SupportPermission.MANAGE_MERCHANTS);
    return supportConsoleService.listMerchants(q);
  }

  @GetMapping("/merchants/{slug}")
  public Map<String, Object> merchantDetail(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable("slug") String slug) {
    access.requirePermission(principal, SupportPermission.MANAGE_MERCHANTS);
    return supportConsoleService.merchantDetail(slug);
  }

  @PostMapping("/merchants")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createMerchant(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody CreateMerchantRequest req) {
    access.requirePermission(principal, SupportPermission.MANAGE_MERCHANTS);
    return supportConsoleService.createMerchant(
        req.name(), req.slug(), req.ownerEmail(), req.ownerPassword(), req.subscriptionPlan());
  }

  @PutMapping("/merchants/{slug}")
  public Map<String, Object> updateMerchant(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @PathVariable("slug") String slug,
      @Valid @RequestBody UpdateMerchantRequest req) {
    access.requirePermission(principal, SupportPermission.MANAGE_MERCHANTS);
    return supportConsoleService.updateMerchant(slug, req.name(), req.slug(), req.subscriptionPlan());
  }

  @DeleteMapping("/merchants/{slug}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMerchant(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable("slug") String slug) {
    access.requirePermission(principal, SupportPermission.MANAGE_MERCHANTS);
    supportConsoleService.deleteMerchant(slug);
  }

  @PostMapping("/merchants/{slug}/reset-owner-password")
  public Map<String, Object> resetOwnerPassword(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @PathVariable("slug") String slug,
      @RequestBody Map<String, Object> body) {
    access.requirePlatformAdmin(principal);
    String password =
        body != null && body.get("password") != null ? String.valueOf(body.get("password")) : "";
    return supportConsoleService.resetOwnerPassword(slug, password, principal);
  }

  @GetMapping("/orders")
  public Map<String, Object> orders(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.VIEW_OPS);
    return Map.of("orders", supportConsoleService.recentOrders());
  }

  @GetMapping("/bookings")
  public Map<String, Object> bookings(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePermission(principal, SupportPermission.VIEW_OPS);
    return Map.of("bookings", supportConsoleService.recentBookings());
  }
}
