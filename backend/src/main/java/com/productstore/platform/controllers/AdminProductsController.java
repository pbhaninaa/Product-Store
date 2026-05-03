package com.productstore.platform.controllers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/products")
public class AdminProductsController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final ProductRepository products;

  public AdminProductsController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      ProductRepository products) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.products = products;
  }

  public record CreateProductJson(
      @NotBlank String name,
      String category,
      @NotNull String price,
      @NotNull String stock,
      String imageUrl) {}

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String, Object> createJson(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody CreateProductJson req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenant.id();
    p.name = req.name().trim();
    p.category =
        req.category() == null || req.category().isBlank()
            ? "Uncategorized"
            : req.category().trim();
    p.priceZar = parseMoney(req.price());
    p.stock = parseStock(req.stock());
    String img = req.imageUrl();
    if (img != null && !img.trim().isEmpty()) {
      p.imageUrl = img.trim();
      p.imagePath = "";
      p.imageData = null;
      p.imageContentType = null;
    } else {
      p.imageUrl = "";
      p.imagePath = "";
      p.imageData = null;
      p.imageContentType = null;
    }
    p.archivedAt = null;
    p.createdAt = Instant.now();
    products.save(p);

    return Map.of("id", p.id.toString());
  }
  @GetMapping("/{productId}/image")
  public ResponseEntity<byte[]> getImage(@PathVariable UUID productId) {
    ProductEntity p = products.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("product_not_found"));

    if (p.imageData == null) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(p.imageContentType))
            .body(p.imageData);
  }
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> createMultipart(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam("name") String name,
      @RequestParam("category") String category,
      @RequestParam("price") String price,
      @RequestParam("stock") String stock,
      @RequestParam(value = "image", required = false) MultipartFile image)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenant.id();
    p.name = name == null ? "" : name.trim();
    p.category = category == null || category.isBlank() ? "Uncategorized" : category.trim();
    p.priceZar = parseMoney(price);
    p.stock = parseStock(stock);
    if (image != null && !image.isEmpty()) {
      p.imageData = image.getBytes();
      p.imageContentType = image.getContentType();
      p.imageUrl = "/api/products/" + p.id + "/image";
      p.imagePath = "";
    } else {
      p.imageUrl = "";
      p.imagePath = "";
      p.imageData = null;
      p.imageContentType = null;
    }
    p.archivedAt = null;
    p.createdAt = Instant.now();
    products.save(p);
    return Map.of("id", p.id.toString());
  }

  public record UpdateProductJson(
      @NotBlank String name,
      String category,
      @NotNull String price,
      @NotNull Integer stock,
      String imageUrl) {}

  @PutMapping(path = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String, Object> updateJson(
      @PathVariable String merchantSlug,
      @PathVariable UUID productId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateProductJson req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    ProductEntity p =
        products
            .findByIdAndTenantId(productId, tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("product_not_found"));
    p.name = req.name().trim();
    p.category =
        req.category() == null || req.category().isBlank()
            ? "Uncategorized"
            : req.category().trim();
    p.priceZar = parseMoney(String.valueOf(req.price()));
    p.stock = Math.max(0, req.stock());
    String imgUpd = req.imageUrl();
    if (imgUpd != null && !imgUpd.isBlank()) {
      p.imageUrl = imgUpd.trim();
      p.imagePath = "";
      p.imageData = null;
      p.imageContentType = null;
    }
    products.save(p);
    return Map.of("ok", true);
  }

  @PutMapping(path = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> updateMultipart(
      @PathVariable String merchantSlug,
      @PathVariable UUID productId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam("name") String name,
      @RequestParam("category") String category,
      @RequestParam("price") String price,
      @RequestParam("stock") String stock,
      @RequestParam(value = "image", required = false) MultipartFile image)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    ProductEntity p =
        products
            .findByIdAndTenantId(productId, tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("product_not_found"));
    p.name = name == null ? "" : name.trim();
    p.category = category == null || category.isBlank() ? "Uncategorized" : category.trim();
    p.priceZar = parseMoney(price);
    p.stock = parseStock(stock);
    if (image != null && !image.isEmpty()) {
      p.imageData = image.getBytes();
      p.imageContentType = image.getContentType();
      p.imageUrl = "/api/products/" + p.id + "/image";
      p.imagePath = "";
    }
    products.save(p);
    return Map.of("ok", true);
  }

  @DeleteMapping("/{productId}")
  @Transactional
  public Map<String, Object> softDelete(
      @PathVariable String merchantSlug,
      @PathVariable UUID productId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    ProductEntity p =
        products
            .findByIdAndTenantId(productId, tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("product_not_found"));
    p.archivedAt = Instant.now();
    products.save(p);
    return Map.of("ok", true);
  }

  private BigDecimal parseMoney(String raw) {
    if (raw == null || raw.isBlank()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
  }

  private int parseStock(String raw) {
    if (raw == null || raw.isBlank()) return 0;
    int n = (int) Math.floor(Double.parseDouble(raw.trim()));
    return Math.max(0, n);
  }

  private void requireMerchantAccess(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}
