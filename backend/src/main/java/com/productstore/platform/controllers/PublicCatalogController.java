package com.productstore.platform.controllers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.services.PlatformFeatureService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}")
public class PublicCatalogController {
  private final TenantAccessService tenantAccess;
  private final ProductRepository products;
  private final PlatformFeatureService platformFeatures;

  public PublicCatalogController(
      TenantAccessService tenantAccess,
      ProductRepository products,
      PlatformFeatureService platformFeatures) {
    this.tenantAccess = tenantAccess;
    this.products = products;
    this.platformFeatures = platformFeatures;
  }

  @GetMapping("/catalog")
  public Map<String, Object> catalog(@PathVariable String merchantSlug) {
    assertCatalogEnabled();
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    var rows = products.findActiveByTenant(tenant.id());

    List<Map<String, Object>> items =
        rows.stream()
            .map(
                p ->
                    Map.<String, Object>of(
                        "id", p.id.toString(),
                        "name", p.name,
                        "category", p.category,
                        "price", p.priceZar.toPlainString(),
                        "imageUrl", publicProductImagePath(merchantSlug, p),
                        "imagePath", p.imagePath,
                        "stock", p.stock))
            .toList();

    return Map.of("merchantSlug", tenant.slug(), "products", items);
  }

  @GetMapping("/products/{productId}/image")
  public ResponseEntity<byte[]> productImage(
      @PathVariable String merchantSlug, @PathVariable UUID productId) {
    assertCatalogEnabled();
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    ProductEntity p =
        products
            .findById(productId)
            .filter(x -> tenant.id().equals(x.tenantId) && x.archivedAt == null)
            .orElseThrow(() -> new IllegalArgumentException("product_not_found"));
    if (p.imageData == null || p.imageData.length == 0) {
      return ResponseEntity.notFound().build();
    }
    String ct =
        p.imageContentType == null || p.imageContentType.isBlank()
            ? "image/jpeg"
            : p.imageContentType;
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct)).body(p.imageData);
  }

  static String publicProductImagePath(String merchantSlug, ProductEntity p) {
    if (p == null || p.id == null) return "";
    if (p.imageData != null && p.imageData.length > 0) {
      return "/api/public/m/" + merchantSlug + "/products/" + p.id + "/image";
    }
    return p.imageUrl == null ? "" : p.imageUrl.trim();
  }

  @GetMapping("/catalog/by-ids")
  public Map<String, Object> catalogByIds(@PathVariable String merchantSlug, String ids) {
    assertCatalogEnabled();
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    List<UUID> parsed =
        (ids == null || ids.isBlank())
            ? List.of()
            : Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
    List<ProductEntity> rows =
        parsed.isEmpty()
            ? List.<ProductEntity>of()
            : products.findActiveByTenantAndIds(tenant.id(), parsed);
    List<Map<String, Object>> items =
        rows.stream()
            .map(
                p ->
                    Map.<String, Object>of(
                        "id", p.id.toString(),
                        "name", p.name,
                        "category", p.category,
                        "price", p.priceZar.toPlainString(),
                        "imageUrl", publicProductImagePath(merchantSlug, p),
                        "imagePath", p.imagePath,
                        "stock", p.stock))
            .toList();
    return Map.of("merchantSlug", tenant.slug(), "products", items);
  }

  private void assertCatalogEnabled() {
    if (!platformFeatures.isEnabled(PlatformFeatureService.PUBLIC_CATALOG)) {
      throw new IllegalStateException("feature_disabled");
    }
  }
}
