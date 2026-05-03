package com.productstore.platform.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/salon")
public class AdminSalonController {
  private final TenantAccessService tenantAccess;
  private final SalonAccessService salonAccess;
  private final MembershipRepository memberships;
  private final SalonServiceRepository services;
  private final SalonStaffRepository staff;
  private final SalonStaffAvailabilityRepository availability;
  private final SalonBookingService salonBooking;

  public AdminSalonController(
      TenantAccessService tenantAccess,
      SalonAccessService salonAccess,
      MembershipRepository memberships,
      SalonServiceRepository services,
      SalonStaffRepository staff,
      SalonStaffAvailabilityRepository availability,
      SalonBookingService salonBooking) {
    this.tenantAccess = tenantAccess;
    this.salonAccess = salonAccess;
    this.memberships = memberships;
    this.services = services;
    this.staff = staff;
    this.availability = availability;
    this.salonBooking = salonBooking;
  }

  @GetMapping("/bookings")
  public List<SalonBookingService.AdminBookingRow> listBookings(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    return salonBooking.listAdminBookings(tenant.id());
  }

  public record BookingStatusBody(@NotBlank String status) {}

  @PostMapping("/bookings/{bookingId}/status")
  @Transactional
  public Map<String, Object> updateBookingStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody BookingStatusBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    salonBooking.updateBookingStatus(tenant.id(), bookingId, body.status());
    return Map.of("ok", true);
  }

  public record EftPaymentDecisionBody(@NotBlank String decision) {}

  @DeleteMapping("/bookings/{bookingId}")
  public Map<String, Object> deleteBookingPermanently(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    boolean ok = salonBooking.deleteBookingPermanentlyIfNotConfirmed(tenant.id(), bookingId);
    return ok ? Map.of("ok", true) : Map.of("ok", false, "reason", "not_deletable");
  }

  public record BookingCashConfirmBody(@NotBlank String code) {}

  @PostMapping("/bookings/{bookingId}/confirm-cash")
  @Transactional
  public Map<String, Object> confirmBookingCash(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody BookingCashConfirmBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    boolean ok = salonBooking.verifyBookingCashPayment(tenant.id(), bookingId, body.code());
    return ok ? Map.of("ok", true) : Map.of("ok", false, "reason", "invalid_code_or_state");
  }

  @PostMapping("/bookings/{bookingId}/eft-payment-decision")
  @Transactional
  public Map<String, Object> decideEftPayment(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody EftPaymentDecisionBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    String d = body.decision().trim().toLowerCase(Locale.ROOT);
    if ("approve".equals(d)) {
      salonBooking.approveManualEftPayment(tenant.id(), bookingId);
    } else if ("reject".equals(d)) {
      salonBooking.rejectManualEftPayment(tenant.id(), bookingId);
    } else {
      throw new IllegalArgumentException("invalid_decision");
    }
    return Map.of("ok", true);
  }

  @GetMapping("/services")
  public List<SalonServiceEntity> listServices(
      @PathVariable String merchantSlug,
      @RequestParam(name = "all", defaultValue = "false") boolean all,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());
    return all ? services.findByTenantIdOrderByCreatedAtDesc(tenant.id()) : services.findActiveByTenant(tenant.id());
  }

  public record UpsertServiceRequest(
      UUID id,
      @NotBlank String name,
      String description,
      @NotNull Integer durationMinutes,
      @NotNull Double priceZar,
      Boolean active) {}

  @PostMapping(value = "/services", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String, Object> upsertService(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpsertServiceRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());

    UUID id = req.id() == null ? UUID.randomUUID() : req.id();
    SalonServiceEntity s =
        services.findByIdAndTenantId(id, tenant.id()).orElseGet(() -> new SalonServiceEntity());
    boolean isNew = (s.id == null);
    if (isNew) {
      s.id = id;
      s.tenantId = tenant.id();
      s.createdAt = Instant.now();
    }

    s.name = req.name().trim();
    s.description = req.description() == null ? "" : req.description().trim();
    s.durationMinutes = Math.max(5, req.durationMinutes());
    s.priceZar = java.math.BigDecimal.valueOf(req.priceZar());
    s.active = req.active() == null ? Boolean.TRUE : req.active();
    if (isNew) {
      s.imageUrl = "";
      s.imagePath = "";
    }
    if (s.imageUrl == null) s.imageUrl = "";
    if (s.imagePath == null) s.imagePath = "";
    services.save(s);

    return Map.of("id", s.id.toString());
  }

  @PostMapping(path = "/services", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> upsertServiceMultipart(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(value = "id", required = false) String idRaw,
      @RequestParam("name") String name,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam("durationMinutes") String durationMinutesRaw,
      @RequestParam("priceZar") String priceZarRaw,
      @RequestParam(value = "active", defaultValue = "true") String activeRaw,
      @RequestParam(value = "image", required = false) MultipartFile image)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    salonAccess.requireSalonShop(tenant.id());

    UUID id;
    if (idRaw == null || idRaw.isBlank()) {
      id = UUID.randomUUID();
    } else {
      id = UUID.fromString(idRaw.trim());
    }
    SalonServiceEntity s =
        services.findByIdAndTenantId(id, tenant.id()).orElseGet(() -> new SalonServiceEntity());
    boolean isNew = (s.id == null);
    if (isNew) {
      s.id = id;
      s.tenantId = tenant.id();
      s.createdAt = Instant.now();
      s.imageUrl = "";
      s.imagePath = "";
    }

    s.name = name == null ? "" : name.trim();
    s.description = description == null ? "" : description.trim();
    int dm = 60;
    try {
      dm = Integer.parseInt(String.valueOf(durationMinutesRaw).trim());
    } catch (RuntimeException ignored) {
    }
    s.durationMinutes = Math.max(5, dm);
    try {
      s.priceZar = java.math.BigDecimal.valueOf(Double.parseDouble(String.valueOf(priceZarRaw).trim()));
    } catch (RuntimeException e) {
      s.priceZar = java.math.BigDecimal.ZERO;
    }
    s.active = !"false".equalsIgnoreCase(String.valueOf(activeRaw).trim());

    if (image != null && !image.isEmpty()) {
      s.imageData = image.getBytes();
      s.imageContentType = image.getContentType();
      s.imageUrl = "/api/m/" + merchantSlug + "/salon/services/" + s.id + "/image";
      s.imagePath = "";
    }
    if (s.imageUrl == null) s.imageUrl = "";
    if (s.imagePath == null) s.imagePath = "";
    if (s.imageData == null) s.imageData = null;
    if (s.imageContentType == null) s.imageContentType = null;

    services.save(s);
    return Map.of("id", s.id.toString());
  }

  @GetMapping("/staff")
  public List<SalonStaffEntity> listStaff(
      @PathVariable String merchantSlug,
      @RequestParam(name = "all", defaultValue = "false") boolean all,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    return all ? staff.findByTenantIdOrderByCreatedAtDesc(tenant.id()) : staff.findActiveByTenant(tenant.id());
  }

  public record UpsertStaffRequest(UUID id, @NotBlank String displayName, Boolean active) {}

  @PostMapping("/staff")
  @Transactional
  public Map<String, Object> upsertStaff(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody UpsertStaffRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    UUID id = req.id() == null ? UUID.randomUUID() : req.id();
    SalonStaffEntity s = staff.findByIdAndTenantId(id, tenant.id()).orElseGet(() -> new SalonStaffEntity());
    boolean isNew = (s.id == null);
    if (isNew) {
      s.id = id;
      s.tenantId = tenant.id();
      s.createdAt = Instant.now();
    }
    s.displayName = req.displayName().trim();
    s.active = req.active() == null ? Boolean.TRUE : req.active();
    staff.save(s);
    return Map.of("id", s.id.toString());
  }

  public record AddAvailabilityRequest(
      @NotNull UUID staffId,
      @NotNull Integer dayOfWeek,
      @NotBlank String startTime,
      @NotBlank String endTime) {}

  @GetMapping("/availability")
  public List<SalonStaffAvailabilityEntity> listAvailability(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    return availability.findByTenantIdOrderByStaffIdAscDayOfWeekAscStartTimeAsc(tenant.id());
  }

  @DeleteMapping("/availability/{id}")
  @Transactional
  public Map<String, Object> deleteAvailability(
      @PathVariable String merchantSlug,
      @PathVariable UUID id,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    SalonStaffAvailabilityEntity a =
        availability.findByIdAndTenantId(id, tenant.id()).orElseThrow(() -> new IllegalArgumentException("not_found"));
    availability.delete(a);
    return Map.of("ok", true);
  }

  @PostMapping("/availability")
  @Transactional
  public Map<String, Object> addAvailability(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody AddAvailabilityRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    // Ensure staff belongs to tenant.
    staff.findByIdAndTenantId(req.staffId(), tenant.id()).orElseThrow(() -> new IllegalArgumentException("staff_not_found"));

    SalonStaffAvailabilityEntity a = new SalonStaffAvailabilityEntity();
    a.id = UUID.randomUUID();
    a.tenantId = tenant.id();
    a.staffId = req.staffId();
    a.dayOfWeek = req.dayOfWeek();
    a.startTime = java.time.LocalTime.parse(req.startTime());
    a.endTime = java.time.LocalTime.parse(req.endTime());
    a.createdAt = Instant.now();
    availability.save(a);
    return Map.of("id", a.id.toString());
  }

  private void requireMerchantAccess(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}

