package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;

import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}")
public class PublicCatalogController {
  private final TenantAccessService tenantAccess;
  private final ProductRepository products;

  public PublicCatalogController(TenantAccessService tenantAccess, ProductRepository products) {
    this.tenantAccess = tenantAccess;
    this.products = products;
  }

  @GetMapping("/catalog")
  public Map<String, Object> catalog(@PathVariable String merchantSlug) {
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
                        "imageUrl", p.imageUrl,
                        "imagePath", p.imagePath,
                        "stock", p.stock))
            .toList();

    return Map.of("merchantSlug", tenant.slug(), "products", items);
  }

  @GetMapping("/catalog/by-ids")
  public Map<String, Object> catalogByIds(@PathVariable String merchantSlug, String ids) {
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
                        "imageUrl", p.imageUrl,
                        "imagePath", p.imagePath,
                        "stock", p.stock))
            .toList();
    return Map.of("merchantSlug", tenant.slug(), "products", items);
  }
}

