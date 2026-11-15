package com.productstore.platform.controllers;

import java.util.List;
import java.util.Map;

import com.productstore.platform.services.NearbyMerchantSearchService;
import com.productstore.platform.services.NearbyMerchantSearchService.Kind;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open provider directory behind the shareable /providers link: lists every active shop and salon so people
 * who were not invited by a provider can still find one. No sign-in (covered by /api/public/** permitAll).
 */
@RestController
@RequestMapping("/api/public/providers")
public class PublicProviderDirectoryController {
  private final NearbyMerchantSearchService directory;

  public PublicProviderDirectoryController(NearbyMerchantSearchService directory) {
    this.directory = directory;
  }

  @GetMapping
  public List<Map<String, Object>> list(
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {
    Kind k = kind == null || kind.isBlank() || "all".equalsIgnoreCase(kind.trim())
        ? null
        : NearbyMerchantSearchService.parseKind(kind);
    if ((latitude == null) != (longitude == null)) {
      throw new IllegalArgumentException("invalid_location");
    }
    return directory.listDirectory(k, latitude, longitude);
  }
}
