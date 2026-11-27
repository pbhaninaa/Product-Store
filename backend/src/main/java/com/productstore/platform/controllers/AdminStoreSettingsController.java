package com.productstore.platform.controllers;

import static com.productstore.platform.constants.MerchantPermission.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.productstore.platform.util.ImageUploads;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.config.PaystackProperties;
import com.productstore.platform.services.MerchantPayoutAccountService;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.ShopOpeningHoursService;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.TenantAccessService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final TenantAccessService tenantAccess;
  private final MerchantAccessService access;
  private final ShopSettingsRepository settings;
  private final ShopOpeningHoursService openingHoursService;
  private final ObjectMapper objectMapper;
  private final MerchantPayoutAccountService payoutAccounts;
  private final PaystackProperties paystackProperties;

  /** The owner's payout bank details: only the owner may read them (staff can read every other setting). */
  static final List<String> OWNER_ONLY_FIELDS =
      List.of("bankName", "bankAccountHolder", "bankAccountNumber", "bankBranchCode");

  public AdminStoreSettingsController(
      TenantAccessService tenantAccess,
      MerchantAccessService access,
      ShopSettingsRepository settings,
      ShopOpeningHoursService openingHoursService,
      ObjectMapper objectMapper,
      MerchantPayoutAccountService payoutAccounts,
      PaystackProperties paystackProperties) {
    this.tenantAccess = tenantAccess;
    this.access = access;
    this.settings = settings;
    this.openingHoursService = openingHoursService;
    this.objectMapper = objectMapper;
    this.payoutAccounts = payoutAccounts;
    this.paystackProperties = paystackProperties;
  }

  @GetMapping("/settings")
  public Map<String, Object> get(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    var caller = access.requireMember(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    Map<String, Object> body = objectMapper.convertValue(s, new TypeReference<LinkedHashMap<String, Object>>() {});
    MerchantPayoutAccountService.AccountView payout = payoutAccounts.view(s);
    body.put("payoutVerified", payout.verified());
    body.put("accountNumberMasked", payout.accountNumberMasked());
    body.put("bankCode", payout.bankCode() == null ? "" : payout.bankCode());
    body.put("payoutAccountVerifiedName", payout.verifiedName() == null ? "" : payout.verifiedName());
    // Never send the full account number over the API; owners see a mask and re-enter to change it.
    body.put("bankAccountNumber", "");
    if (!caller.owner()) {
      OWNER_ONLY_FIELDS.forEach(f -> body.put(f, ""));
      body.put("bankCode", "");
      body.put("accountNumberMasked", "");
      body.put("payoutAccountVerifiedName", "");
      body.put("payoutVerified", false);
    }
    return body;
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
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
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

  /**
   * Payout bank details and the customer payment toggles. Manual EFT was removed (2026-11-23), so there is no EFT
   * toggle or EFT instruction text any more; old clients that still send them are ignored.
   *
   * <p>For South African banks, Paystack requires account validation with identity verification. The account type
   * ("personal" or "business"), document type, and document number are required for validation but are NOT
   * stored permanently - they're only used to validate the account with Paystack and then discarded for privacy.
   */
  public record UpdateBankingRequest(
      String bankName,
      String bankAccountHolder,
      String bankAccountNumber,
      String bankBranchCode,
      String bankCode,
      String confirmedAccountName,
      String accountType,
      String documentType,
      String documentNumber,
      Boolean acceptCustomerOnline,
      Boolean acceptCustomerCash) {}

  @PutMapping("/banking")
  @Transactional
  public Map<String, Object> updateBanking(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateBankingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    // Banking decides where customer money goes: owner-only whatever permissions say.
    access.requireOwner(principal, tenant.id());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    boolean changingPayout =
        req.bankCode() != null
            || req.bankAccountNumber() != null
            || req.confirmedAccountName() != null
            || req.bankName() != null
            || req.bankAccountHolder() != null
            || req.bankBranchCode() != null;
    if (changingPayout) {
      if (paystackProperties.isConfigured()
          && req.bankAccountNumber() != null
          && !req.bankAccountNumber().isBlank()) {
        payoutAccounts.save(
            tenant.id(),
            req.bankCode(),
            req.bankAccountNumber(),
            req.confirmedAccountName(),
            req.accountType(),
            req.documentType(),
            req.documentNumber());
        s = settings.findByTenantId(tenant.id()).orElseThrow();
      } else {
        if (req.bankName() != null) {
          s.bankName = req.bankName().trim();
        }
        if (req.bankAccountHolder() != null) {
          s.bankAccountHolder = req.bankAccountHolder().trim();
        }
        if (req.bankAccountNumber() != null) {
          s.bankAccountNumber = req.bankAccountNumber().trim();
        }
        if (req.bankBranchCode() != null) {
          s.bankBranchCode = req.bankBranchCode().trim();
        }
        if (req.bankCode() != null) {
          s.bankCode = req.bankCode().trim();
        }
        s.payoutAccountVerifiedName = "";
        s.payoutPaystackRecipientCode = null;
      }
    }
    if (req.acceptCustomerOnline() != null) {
      s.acceptCustomerOnline = req.acceptCustomerOnline();
    }
    if (req.acceptCustomerCash() != null) {
      s.acceptCustomerCash = req.acceptCustomerCash();
    }
    boolean onlineOff = Boolean.FALSE.equals(s.acceptCustomerOnline);
    boolean cashOff = Boolean.FALSE.equals(s.acceptCustomerCash);
    if (onlineOff && cashOff) {
      throw new IllegalArgumentException("payment_options_required");
    }
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true);
  }

  @GetMapping("/payout-banks")
  public List<Map<String, String>> payoutBanks(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    return payoutAccounts.listBanks().stream()
        .map(b -> Map.of("code", b.code(), "name", b.name()))
        .toList();
  }

  public record PreviewPayoutAccountRequest(String bankCode, String accountNumber) {}

  @PostMapping("/payout-account/preview")
  public MerchantPayoutAccountService.ResolvedAccount previewPayoutAccount(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody PreviewPayoutAccountRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    return payoutAccounts.preview(req == null ? null : req.bankCode(), req == null ? null : req.accountNumber());
  }

  /** Booking fee for pay-in-store bookings: a whole percent from 0 (no fee) to 50. */
  public record UpdateBookingFeeRequest(java.math.BigDecimal bookingFeePercent) {}

  /**
   * Sets the booking fee percent (booking fee FR-1, AC-1 to AC-3). Needs the store settings permission (the owner
   * always has it). Existing bookings keep the fee fixed when they were made (BR-7).
   *
   * @throws IllegalArgumentException {@code booking_fee_percent_out_of_range} (400) outside 0 to 50 or fractional
   */
  @PutMapping("/booking-fee")
  @Transactional
  public Map<String, Object> updateBookingFee(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody UpdateBookingFeeRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
    int percent =
        com.productstore.platform.services.BookingFeePolicy.validatePercent(req == null ? null : req.bookingFeePercent());
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    s.bookingFeePercent = percent;
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true, "bookingFeePercent", percent);
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
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
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
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
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
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));

    s.storeName = storeName == null ? "" : storeName.trim();
    applyShopTypeParam(s, shopType);

    if (logo != null && !logo.isEmpty()) {
      var img = ImageUploads.read(logo, true);
      s.storeLogoData = img.data();
      s.storeLogoContentType = img.contentType();
      s.storeLogoUrl = "/api/public/m/" + merchantSlug + "/branding/logo";
    } else if (truthy(removeLogo)) {
      s.storeLogoUrl = "";
      s.storeLogoData = null;
      s.storeLogoContentType = null;
    }

    if (hero != null && !hero.isEmpty()) {
      var img = ImageUploads.read(hero, true);
      s.storeHeroData = img.data();
      s.storeHeroContentType = img.contentType();
      s.storeHeroUrl = "/api/public/m/" + merchantSlug + "/branding/hero";
    } else if (truthy(removeHero)) {
      s.storeHeroUrl = "";
      s.storeHeroData = null;
      s.storeHeroContentType = null;
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

  /** {@code normal_store} or {@code salon} (see {@link SalonAccessService}). */
  public record UpdateShopTypeRequest(@NotBlank String shopType) {}

  @PutMapping("/shop-type")
  @Transactional
  public Map<String, Object> updateShopType(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpdateShopTypeRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
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
    d.bankCode = "";
    d.payoutAccountVerifiedName = "";
    d.storeName = "";
    d.contactEmail = "";
    d.contactPhone = "";
    d.contactAddress = "";
    d.contactNotes = "";
    d.storeLogoUrl = "";
    d.storeHeroUrl = "";
    d.shopType = SalonAccessService.SHOP_NORMAL;
    d.openingHoursJson = null;
    d.acceptCustomerOnline = Boolean.TRUE;
    d.acceptCustomerEft = Boolean.FALSE;
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
    access.requireAll(principal, tenant.id(), STORE_SETTINGS);
    ShopSettingsEntity s = settings.findByTenantId(tenant.id()).orElseGet(() -> createDefaults(tenant.id()));
    s.openingHoursJson =
        openingHoursService.normalizeJsonForStorage(
            req.openingHoursJson() == null ? "[]" : req.openingHoursJson());
    s.updatedAt = Instant.now();
    settings.save(s);
    return Map.of("ok", true, "openingHoursJson", s.openingHoursJson == null ? "[]" : s.openingHoursJson);
  }
}

