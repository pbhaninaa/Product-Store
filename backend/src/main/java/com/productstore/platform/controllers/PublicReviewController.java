package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.ReviewService;
import com.productstore.platform.services.TenantAccessService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/reviews")
public class PublicReviewController {
  private final TenantAccessService tenantAccess;
  private final ReviewService reviews;

  public PublicReviewController(TenantAccessService tenantAccess, ReviewService reviews) {
    this.tenantAccess = tenantAccess;
    this.reviews = reviews;
  }

  @GetMapping("/summary")
  public Map<String, Object> summary(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return reviews.summary(tenant.id());
  }

  public record ReviewBody(
      @NotBlank String kind,
      @NotNull UUID id,
      @Email @NotBlank String customerEmail,
      @Min(1) @Max(5) int rating,
      String comment) {}

  @PostMapping
  public Map<String, Object> submit(
      @PathVariable String merchantSlug, @Valid @RequestBody ReviewBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    String kind = body.kind().trim().toLowerCase();
    if ("order".equals(kind)) {
      return reviews.submitOrderReview(
          tenant.id(), body.id(), body.customerEmail(), body.rating(), body.comment());
    }
    if ("booking".equals(kind)) {
      return reviews.submitBookingReview(
          tenant.id(), body.id(), body.customerEmail(), body.rating(), body.comment());
    }
    throw new IllegalArgumentException("invalid_kind");
  }

  @GetMapping("/rated")
  public Map<String, Object> rated(
      @PathVariable String merchantSlug,
      @RequestParam String kind,
      @RequestParam UUID id) {
    tenantAccess.requireTenantBySlug(merchantSlug);
    String k = kind == null ? "" : kind.trim().toLowerCase();
    boolean rated =
        "booking".equals(k) ? reviews.bookingAlreadyRated(id) : reviews.orderAlreadyRated(id);
    return Map.of("rated", rated);
  }
}
