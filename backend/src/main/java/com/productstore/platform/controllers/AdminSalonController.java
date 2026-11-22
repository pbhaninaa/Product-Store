package com.productstore.platform.controllers;

import static com.productstore.platform.constants.MerchantPermission.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.util.ImageUploads;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.services.EmployeeService;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.SalonStaffSyncService;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

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
  private final MerchantAccessService access;
  private final SalonServiceRepository services;
  private final SalonStaffRepository staff;
  private final SalonStaffAvailabilityRepository availability;
  private final SalonBookingService salonBooking;
  private final SalonStaffSyncService salonStaffSync;
  private final EmployeeService employeeService;

  public AdminSalonController(
      TenantAccessService tenantAccess,
      SalonAccessService salonAccess,
      MerchantAccessService access,
      SalonServiceRepository services,
      SalonStaffRepository staff,
      SalonStaffAvailabilityRepository availability,
      SalonBookingService salonBooking,
      SalonStaffSyncService salonStaffSync,
      EmployeeService employeeService) {
    this.employeeService = employeeService;
    this.tenantAccess = tenantAccess;
    this.salonAccess = salonAccess;
    this.access = access;
    this.services = services;
    this.staff = staff;
    this.availability = availability;
    this.salonBooking = salonBooking;
    this.salonStaffSync = salonStaffSync;
  }

  @GetMapping("/bookings")
  public List<SalonBookingService.AdminBookingRow> listBookings(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    // Read-only list is shared by the Bookings and Payments pages.
    access.requireAny(principal, tenant.id(), BOOKINGS, PAYMENTS);
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
    var caller = access.requireAll(principal, tenant.id(), BOOKINGS);
    if ("cancelled".equalsIgnoreCase(body.status().trim())) {
      MerchantAccessService.check(caller, CANCEL_BOOKINGS);
    }
    salonAccess.requireSalonShop(tenant.id());
    salonBooking.updateBookingStatus(tenant.id(), bookingId, body.status());
    employeeService.creditBookingIfUnattributed(tenant.id(), bookingId, principal.userId());
    return Map.of("ok", true);
  }

  public record EftPaymentDecisionBody(@NotBlank String decision) {}

  @DeleteMapping("/bookings/{bookingId}")
  public Map<String, Object> deleteBookingPermanently(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), BOOKINGS, DELETE_RECORDS);
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
    access.requireAll(principal, tenant.id(), BOOKINGS, CONFIRM_PAYMENTS);
    salonAccess.requireSalonShop(tenant.id());
    boolean ok = salonBooking.verifyBookingCashPayment(tenant.id(), bookingId, body.code());
    if (ok) employeeService.creditBookingIfUnattributed(tenant.id(), bookingId, principal.userId());
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
    access.requireAll(principal, tenant.id(), BOOKINGS, CONFIRM_PAYMENTS);
    salonAccess.requireSalonShop(tenant.id());
    String d = body.decision().trim().toLowerCase(Locale.ROOT);
    if ("approve".equals(d)) {
      salonBooking.approveManualEftPayment(tenant.id(), bookingId);
      employeeService.creditBookingIfUnattributed(tenant.id(), bookingId, principal.userId());
    } else if ("reject".equals(d)) {
      salonBooking.rejectManualEftPayment(tenant.id(), bookingId);
    } else {
      throw new IllegalArgumentException("invalid_decision");
    }
    return Map.of("ok", true);
  }

  @GetMapping("/services")
  public List<Map<String, Object>> listServices(
      @PathVariable String merchantSlug,
      @RequestParam(name = "all", defaultValue = "false") boolean all,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), SERVICES);
    salonAccess.requireSalonShop(tenant.id());
    List<SalonServiceEntity> rows =
        all
            ? services.findByTenantIdOrderByCreatedAtDesc(tenant.id())
            : services.findActiveByTenant(tenant.id());
    return rows.stream().map(s -> toServiceRow(merchantSlug, s)).toList();
  }

  private static Map<String, Object> toServiceRow(String merchantSlug, SalonServiceEntity s) {
    Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("id", s.id.toString());
    m.put("tenantId", s.tenantId.toString());
    m.put("name", s.name);
    m.put("description", s.description);
    m.put("durationMinutes", s.durationMinutes);
    m.put("priceZar", s.priceZar != null ? s.priceZar.toPlainString() : "0");
    m.put("active", s.active);
    m.put("createdAt", s.createdAt != null ? s.createdAt.toString() : null);
    m.put("imageUrl", PublicSalonController.publicServiceImagePath(merchantSlug, s));
    m.put("imagePath", s.imagePath == null ? "" : s.imagePath);
    m.put("hasImage", s.imageData != null && s.imageData.length > 0);
    return m;
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
    access.requireAll(principal, tenant.id(), SERVICES);
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
    access.requireAll(principal, tenant.id(), SERVICES);
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
      var img = ImageUploads.read(image);
      s.imageData = img.data();
      s.imageContentType = img.contentType();
      s.imageUrl = "/api/public/m/" + merchantSlug + "/salon/services/" + s.id + "/image";
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
  @Transactional
  public List<SalonStaffEntity> listStaff(
      @PathVariable String merchantSlug,
      @RequestParam(name = "all", defaultValue = "false") boolean all,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    // Staff names are needed by every bookings / services screen.
    access.requireMember(principal, tenant.id());
    salonStaffSync.syncTenantFromPayroll(tenant.id());
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
    access.requireAll(principal, tenant.id(), SERVICES);

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
    access.requireAll(principal, tenant.id(), SERVICES);
    return availability.findByTenantIdOrderByStaffIdAscDayOfWeekAscStartTimeAsc(tenant.id());
  }

  @DeleteMapping("/availability/{id}")
  @Transactional
  public Map<String, Object> deleteAvailability(
      @PathVariable String merchantSlug,
      @PathVariable UUID id,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), SERVICES);
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
    access.requireAll(principal, tenant.id(), SERVICES);

    // Ensure staff belongs to tenant.
    staff.findByIdAndTenantId(req.staffId(), tenant.id()).orElseThrow(() -> new IllegalArgumentException("staff_not_found"));

    int day = req.dayOfWeek();
    if (day < 1 || day > 7) throw new IllegalArgumentException("invalid_day_of_week");
    java.time.LocalTime start;
    java.time.LocalTime end;
    try {
      start = java.time.LocalTime.parse(req.startTime().trim());
      end = java.time.LocalTime.parse(req.endTime().trim());
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("invalid_time");
    }
    if (!end.isAfter(start)) throw new IllegalArgumentException("availability_end_before_start");

    // One person can't have two windows covering the same time on the same day (an exact repeat included);
    // windows that only touch, e.g. 08:00–12:00 and 12:00–17:00, are fine.
    for (SalonStaffAvailabilityEntity existing :
        availability.findForStaffOnDay(tenant.id(), List.of(req.staffId()), day)) {
      if (start.isBefore(existing.endTime) && existing.startTime.isBefore(end)) {
        throw new IllegalStateException("availability_overlap");
      }
    }

    SalonStaffAvailabilityEntity a = new SalonStaffAvailabilityEntity();
    a.id = UUID.randomUUID();
    a.tenantId = tenant.id();
    a.staffId = req.staffId();
    a.dayOfWeek = day;
    a.startTime = start;
    a.endTime = end;
    a.createdAt = Instant.now();
    availability.save(a);
    return Map.of("id", a.id.toString());
  }
}

