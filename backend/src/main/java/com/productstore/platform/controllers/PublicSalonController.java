package com.productstore.platform.controllers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.PeachPaymentService;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.repositories.SalonServiceRepository;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/salon")
public class PublicSalonController {
  private final TenantAccessService tenantAccess;
  private final SalonAccessService salonAccess;
  private final SalonBookingService salon;
  private final SalonServiceRepository salonServices;
  private final PeachPaymentService peachPaymentService;

  public PublicSalonController(
      TenantAccessService tenantAccess,
      SalonAccessService salonAccess,
      SalonBookingService salon,
      SalonServiceRepository salonServices,
      PeachPaymentService peachPaymentService) {
    this.tenantAccess = tenantAccess;
    this.salonAccess = salonAccess;
    this.salon = salon;
    this.salonServices = salonServices;
    this.peachPaymentService = peachPaymentService;
  }

  @GetMapping("/services")
  public Map<String, Object> services(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    if (!salonAccess.isSalonShop(tenant.id())) {
      return Map.of(
          "merchantSlug", tenant.slug(),
          "salonEnabled", Boolean.FALSE,
          "shopType",
          salonAccess.normalizedShopType(tenant.id()),
          "services",
          List.<Map<String, Object>>of());
    }
    var rows = salon.listActiveServices(tenant.id());
    var items =
        rows.stream()
            .map(
                s -> {
                  String img = publicServiceImagePath(merchantSlug, s);
                  return Map.<String, Object>of(
                      "id", s.id.toString(),
                      "name", s.name,
                      "description", s.description,
                      "durationMinutes", s.durationMinutes,
                      "priceZar", s.priceZar.toPlainString(),
                      "imageUrl", img);
                })
            .toList();
    return Map.of(
        "merchantSlug",
        tenant.slug(),
        "salonEnabled",
        Boolean.TRUE,
        "shopType",
        salonAccess.normalizedShopType(tenant.id()),
        "services",
        items);
  }

  @GetMapping("/services/{serviceId}/image")
  public ResponseEntity<byte[]> serviceImage(
      @PathVariable String merchantSlug, @PathVariable String serviceId) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    UUID sid = UUID.fromString(serviceId);
    SalonServiceEntity s =
        salonServices
            .findByIdAndTenantId(sid, tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    if (s.imageData == null || s.imageData.length == 0) {
      return ResponseEntity.notFound().build();
    }
    String ct =
        s.imageContentType == null || s.imageContentType.isBlank()
            ? "image/jpeg"
            : s.imageContentType;
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct)).body(s.imageData);
  }

  static String publicServiceImagePath(String merchantSlug, SalonServiceEntity s) {
    if (s == null || s.id == null) return "";
    if (s.imageData != null && s.imageData.length > 0) {
      return "/api/public/m/" + merchantSlug + "/salon/services/" + s.id + "/image";
    }
    return s.imageUrl == null ? "" : s.imageUrl.trim();
  }

  @GetMapping("/availability")
  public Map<String, Object> availability(
      @PathVariable String merchantSlug,
      @RequestParam String serviceId,
      @RequestParam String date) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    UUID sid = UUID.fromString(serviceId);
    LocalDate d = LocalDate.parse(date);
    List<SalonBookingService.Slot> slots = salon.getAvailableSlots(tenant.id(), sid, d);
    var items =
        slots.stream()
            .map(
                s ->
                    Map.<String, Object>of(
                        "startAt", s.startAt().toString(),
                        "endAt", s.endAt().toString(),
                        "capacity", s.capacity(),
                        "booked", s.booked()))
            .toList();
    return Map.of("merchantSlug", tenant.slug(), "serviceId", serviceId, "date", date, "slots", items);
  }

  public record CreateBookingRequest(
      String serviceId,
      String startAt,
      String customerName,
      String customerPhone,
      String customerEmail,
      /** {@code peach} or {@code cash_store}; required when the shop accepts both. Manual EFT is disabled. */
      String paymentMethod,
      /** Required for Peach: {@code CARD} or {@code EFT} (Instant EFT). */
      String peachPaymentMethod) {}

  @PostMapping("/bookings")
  public Map<String, Object> createBooking(
      @PathVariable String merchantSlug, @RequestBody CreateBookingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    UUID sid = UUID.fromString(req.serviceId());
    Instant startAt = Instant.parse(req.startAt());
    SalonBookingService.CreatedBooking created =
        salon.createBooking(
            tenant.id(),
            sid,
            req.customerName(),
            req.customerPhone(),
            req.customerEmail(),
            startAt,
            req.paymentMethod(),
            req.peachPaymentMethod());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("bookingId", created.bookingId().toString());
    out.put("paymentMethod", created.paymentMethod());
    out.put("paymentReferenceHint", created.paymentReferenceHint());
    out.put("bookingStatus", created.bookingStatus());
    if (created.cashPaymentCode() != null && !created.cashPaymentCode().isBlank()) {
      out.put("cashPaymentCode", created.cashPaymentCode());
      out.put("needsCashPaymentCode", Boolean.TRUE);
    }
    if ("peach".equalsIgnoreCase(created.paymentMethod())) {
      PeachPaymentService.PeachCheckoutSession session =
          peachPaymentService.initiateBookingCheckout(tenant.id(), created.bookingId(), merchantSlug);
      out.put("peachCheckoutId", session.checkoutId());
      out.put("peachRedirectUrl", session.redirectUrl());
      out.put("needsPeachCheckout", Boolean.TRUE);
    }
    return out;
  }

  @PostMapping(path = "/bookings/{bookingId}/eft-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> submitEftProof(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @RequestParam("customerEmail") String customerEmail,
      @RequestParam("bankReference") String bankReference,
      @RequestParam("proof") MultipartFile proof)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    return salon.submitEftProof(tenant.id(), bookingId, customerEmail, bankReference, proof);
  }
}

