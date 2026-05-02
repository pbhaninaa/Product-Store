package com.productstore.platform.controllers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.ShopOpeningHoursService;
import com.productstore.platform.services.TenantAccessService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/store")
public class AdminStoreSettingsController {
  private static final Set<String> IMG_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final ShopSettingsRepository settings;
  private final ShopOpeningHoursService openingHoursService;
  private final String uploadsDir;
  private final String publicBaseUrl;

  public AdminStoreSettingsController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      ShopSettingsRepository settings,
      ShopOpeningHoursService openingHoursService,
      @Value("${app.uploads.dir:./data/uploads}") String uploadsDir,
      @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.settings = settings;
    this.openingHoursService = openingHoursService;
    this.uploadsDir = uploadsDir;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  @GetMapping("/settings")
  public ShopSettingsEntity get(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    return settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
  }

  public record UpdateDeliveryRequest(
      @NotBlank String deliveryFeeMode,
      @NotNull Double deliveryFeeZar,
      @NotNull Double deliveryFeePerKmZar,
      Double storeLat,
      Double storeLng) {}

  @PutMapping("/delivery")
  @Transactional
  public Map<String, Object> updateDelivery(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateDeliveryRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    s.deliveryFeeMode = "per_km".equalsIgnoreCase(req.deliveryFeeMode()) ? "per_km" : "standard";
    s.deliveryFeeFlatZar = java.math.BigDecimal.valueOf(req.deliveryFeeZar());
    s.deliveryFeePerKmZar = java.math.BigDecimal.valueOf(req.deliveryFeePerKmZar());
    s.storeLat = req.storeLat();
    s.storeLng = req.storeLng();
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true);
  }

  public record UpdateBankingRequest(
      @NotBlank String bankName,
      @NotBlank String bankAccountHolder,
      @NotBlank String bankAccountNumber,
      String bankBranchCode,
      String eftBankInstructions,
      Boolean acceptCustomerEft,
      Boolean acceptCustomerCash) {}

  @PutMapping("/banking")
  @Transactional
  public Map<String, Object> updateBanking(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateBankingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    s.bankName = req.bankName().trim();
    s.bankAccountHolder = req.bankAccountHolder().trim();
    s.bankAccountNumber = req.bankAccountNumber().trim();
    s.bankBranchCode = req.bankBranchCode() == null ? "" : req.bankBranchCode().trim();
    s.eftBankInstructions = req.eftBankInstructions() == null ? "" : req.eftBankInstructions().trim();
    if (req.acceptCustomerEft() != null) {
      s.acceptCustomerEft = req.acceptCustomerEft();
    }
    if (req.acceptCustomerCash() != null) {
      s.acceptCustomerCash = req.acceptCustomerCash();
    }
    if (Boolean.FALSE.equals(s.acceptCustomerEft) && Boolean.FALSE.equals(s.acceptCustomerCash)) {
      throw new IllegalArgumentException("payment_options_required");
    }
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true);
  }

  public record UpdateContactRequest(
      String storeName,
      String contactEmail,
      String contactPhone,
      String contactAddress,
      String contactNotes,
      String storeLogoUrl,
      String storeHeroUrl) {}

  @PutMapping("/contact")
  @Transactional
  public Map<String, Object> updateContact(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateContactRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    s.storeName = req.storeName() == null ? "" : req.storeName().trim();
    s.contactEmail = req.contactEmail() == null ? "" : req.contactEmail().trim();
    s.contactPhone = req.contactPhone() == null ? "" : req.contactPhone().trim();
    s.contactAddress = req.contactAddress() == null ? "" : req.contactAddress().trim();
    s.contactNotes = req.contactNotes() == null ? "" : req.contactNotes().trim();
    s.storeLogoUrl = req.storeLogoUrl() == null ? "" : req.storeLogoUrl().trim();
    s.storeHeroUrl = req.storeHeroUrl() == null ? "" : req.storeHeroUrl().trim();
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true);
  }

  public record UpdateBrandingRequest(String storeName, String shopType) {}

  @PutMapping(value = "/branding", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String, Object> updateBranding(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateBrandingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    s.storeName = req.storeName() == null ? "" : req.storeName().trim();
    applyShopTypeParam(s, req.shopType());
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of(
        "ok",
        true,
        "shopType",
        SalonAccessService.normalizedShopType(s.shopType));
  }

  @PutMapping(value = "/branding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> updateBrandingMultipart(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(value = "storeName", required = false) String storeName,
      @RequestParam(value = "shopType", required = false) String shopType,
      @RequestParam(value = "removeLogo", required = false) String removeLogo,
      @RequestParam(value = "removeHero", required = false) String removeHero,
      @RequestParam(value = "logo", required = false) MultipartFile logo,
      @RequestParam(value = "hero", required = false) MultipartFile hero)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    s.storeName = storeName == null ? "" : storeName.trim();
    applyShopTypeParam(s, shopType);

    if (logo != null && !logo.isEmpty()) {
      s.storeLogoUrl = storeBrandingImagePublicUrl(tenant.id(), "logo", logo);
    } else if (truthy(removeLogo)) {
      s.storeLogoUrl = "";
    }

    if (hero != null && !hero.isEmpty()) {
      s.storeHeroUrl = storeBrandingImagePublicUrl(tenant.id(), "hero", hero);
    } else if (truthy(removeHero)) {
      s.storeHeroUrl = "";
    }

    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of(
        "ok",
        true,
        "shopType",
        SalonAccessService.normalizedShopType(s.shopType),
        "storeLogoUrl",
        s.storeLogoUrl == null ? "" : s.storeLogoUrl,
        "storeHeroUrl",
        s.storeHeroUrl == null ? "" : s.storeHeroUrl);
  }

  private static void applyShopTypeParam(ShopSettingsEntity s, String shopType) {
    if (shopType != null && !shopType.isBlank()) {
      s.shopType = SalonAccessService.canonicalShopTypeForSave(shopType.trim());
    }
  }

  private static boolean truthy(String v) {
    if (v == null) {
      return false;
    }
    String t = v.trim();
    return t.equalsIgnoreCase("true") || t.equals("1") || t.equalsIgnoreCase("yes");
  }

  private String storeBrandingImagePublicUrl(UUID tenantId, String kind, MultipartFile file) throws Exception {
    String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String ext = extension(original);
    if (!IMG_EXT.contains(ext)) {
      throw new IllegalArgumentException("unsupported_image_type");
    }

    Path dir = Paths.get(uploadsDir, "branding", tenantId.toString()).toAbsolutePath().normalize();
    Files.createDirectories(dir);
    Path target = dir.resolve(kind + "." + ext);

    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    String rel = "branding/" + tenantId + "/" + kind + "." + ext;
    return publicBaseUrl + "/uploads/" + rel;
  }

  private static String extension(String filename) {
    int i = filename.lastIndexOf('.');
    if (i < 0 || i >= filename.length() - 1) {
      return "";
    }
    return filename.substring(i + 1).toLowerCase(Locale.ROOT);
  }

  /** {@code normal_store} or {@code salon} (see {@link SalonAccessService}). */
  public record UpdateShopTypeRequest(@NotBlank String shopType) {}

  @PutMapping("/shop-type")
  @Transactional
  public Map<String, Object> updateShopType(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateShopTypeRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    String next = SalonAccessService.canonicalShopTypeForSave(req.shopType().trim());
    s.shopType = next;
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true, "shopType", next);
  }

  private ShopSettingsEntity createDefaults(UUID tenantId) {
    ShopSettingsEntity d = new ShopSettingsEntity();
    d.id = UUID.randomUUID();
    d.tenantId = tenantId;
    d.deliveryFeeMode = "standard";
    d.deliveryFeeFlatZar = java.math.BigDecimal.valueOf(50.00);
    d.deliveryFeePerKmZar = java.math.BigDecimal.valueOf(8.00);
    d.storeLat = null;
    d.storeLng = null;
    d.eftBankInstructions = "";
    d.bankName = "";
    d.bankAccountHolder = "";
    d.bankAccountNumber = "";
    d.bankBranchCode = "";
    d.storeName = "";
    d.contactEmail = "";
    d.contactPhone = "";
    d.contactAddress = "";
    d.contactNotes = "";
    d.storeLogoUrl = "";
    d.storeHeroUrl = "";
    d.shopType = SalonAccessService.SHOP_NORMAL;
    d.openingHoursJson = null;
    d.acceptCustomerEft = Boolean.TRUE;
    d.acceptCustomerCash = Boolean.TRUE;
    d.createdAt = Instant.now();
    d.updatedAt = Instant.now();
    return settings.save(d);
  }

  public record UpdateOpeningHoursRequest(String openingHoursJson) {}

  /**
   * Accepts PUT (REST) and POST (some proxies / older clients mishandle PUT). Same JSON body.
   */
  @RequestMapping(
      value = "/opening-hours",
      method = {RequestMethod.PUT, RequestMethod.POST},
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String, Object> updateOpeningHours(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody UpdateOpeningHoursRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    s.openingHoursJson =
        openingHoursService.normalizeJsonForStorage(
            req.openingHoursJson() == null ? "[]" : req.openingHoursJson());
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true, "openingHoursJson", s.openingHoursJson == null ? "[]" : s.openingHoursJson);
  }

  private void requireMerchantAccess(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, java.util.List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}

