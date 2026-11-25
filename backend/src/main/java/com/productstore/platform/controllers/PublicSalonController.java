package com.productstore.platform.controllers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.util.ImageUploads;
import com.productstore.platform.services.PaystackPaymentService;
import com.productstore.platform.services.SalonAccessService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.repositories.SalonServiceRepository;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/salon")
public class PublicSalonController {
  private final TenantAccessService tenantAccess;
  private final SalonAccessService salonAccess;
  private final SalonBookingService salon;
  private final SalonServiceRepository salonServices;
  private final PaystackPaymentService paystack;

  public PublicSalonController(
      TenantAccessService tenantAccess,
      SalonAccessService salonAccess,
      SalonBookingService salon,
      SalonServiceRepository salonServices,
      PaystackPaymentService paystack) {
    this.tenantAccess = tenantAccess;
    this.salonAccess = salonAccess;
    this.salon = salon;
    this.salonServices = salonServices;
    this.paystack = paystack;
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
      @PathVariable String merchantSlug,
      @PathVariable String serviceId,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    UUID sid = UUID.fromString(serviceId);
    SalonServiceEntity s =
        salonServices
            .findByIdAndTenantId(sid, tenant.id())
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    return ImageUploads.respond(s.imageData, s.imageContentType, ifNoneMatch);
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
                        "booked", s.booked(),
                        "full", s.full()))
            .toList();
    return Map.of("merchantSlug", tenant.slug(), "serviceId", serviceId, "date", date, "slots", items);
  }

  public record CreateBookingRequest(
      String serviceId,
      String startAt,
      String customerName,
      String customerPhone,
      String customerEmail,
      /**
       * {@code cash_store} or {@code online}; required when the shop accepts both. {@code eft} (Manual EFT) is
       * refused with {@code eft_not_accepted}.
       */
      String paymentMethod,
      /** Required for {@code online}: {@code CARD} or {@code EFT}. */
      String onlinePaymentChannel,
      String promoId) {}

  @PostMapping("/bookings")
  public Map<String, Object> createBooking(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody CreateBookingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    UUID sid = UUID.fromString(req.serviceId());
    Instant startAt = Instant.parse(req.startAt());
    UUID promoId = null;
    if (req.promoId() != null && !req.promoId().isBlank()) {
      try {
        promoId = UUID.fromString(req.promoId().trim());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("invalid_promo");
      }
    }
    UUID clientUserId = null;
    if (principal != null && principal.roles().stream().anyMatch(r -> r == Role.CLIENT)) {
      clientUserId = principal.userId();
    }
    SalonBookingService.CreatedBooking created =
        salon.createBooking(
            tenant.id(),
            sid,
            req.customerName(),
            req.customerPhone(),
            req.customerEmail(),
            startAt,
            req.paymentMethod(),
            req.onlinePaymentChannel(),
            promoId,
            clientUserId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("bookingId", created.bookingId().toString());
    out.put("paymentMethod", created.paymentMethod());
    out.put("paymentReferenceHint", created.paymentReferenceHint());
    out.put("bookingStatus", created.bookingStatus());
    if (created.handoverCode() != null && !created.handoverCode().isBlank()) {
      out.put("handoverCode", created.handoverCode());
      if (SalonBookingEntity.ClientPaymentMethod.cash_store.name().equals(created.paymentMethod())) {
        // Older clients only know the cash code; it is the same handover code.
        out.put("cashPaymentCode", created.handoverCode());
        out.put("needsCashPaymentCode", Boolean.TRUE);
      }
    }
    if (created.bookingFee() != null) {
      out.put("bookingFee", created.bookingFee());
    }
    // In-app bookings pay the full price online; a cash booking with a booking fee pays only the fee online now
    // (the booking is cancelled and its slot freed if that checkout cannot start).
    if (SalonBookingEntity.ClientPaymentMethod.online.name().equals(created.paymentMethod())
        || created.needsFeeCheckout()) {
      paystack.startNewBookingCheckout(tenant.id(), created.bookingId(), merchantSlug).addTo(out);
    }
    return out;
  }

  /** The customer's proof for cancelling from the public booking page: the booking email. */
  public record CancelBookingRequest(String customerEmail) {}

  /**
   * A customer cancels their own pay-in-store booking (booking fee FR-8). The full booking id (from the booking
   * link) and the booking email are required. The booking fee is refunded when cancelled at least 24 hours before the
   * start, else kept by the provider; the response says which ({@code bookingFeeOutcome}).
   */
  @PostMapping("/bookings/{bookingId}/cancel")
  public Map<String, Object> cancelBooking(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @RequestBody CancelBookingRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    UUID cancelled = salon.cancelByCustomer(tenant.id(), bookingId, req == null ? null : req.customerEmail(), null);
    // Read after the commit, so the answer shows whether Paystack accepted the refund.
    return salon.customerCancellationResult(tenant.id(), cancelled);
  }

  /** Return page: payment state of a booking, confirming a still pending online payment with Paystack. */
  @GetMapping("/bookings/{bookingId}/payment-status")
  public Map<String, Object> bookingPaymentStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @RequestParam(value = "customerEmail", required = false) String customerEmail,
      @RequestParam(value = "reference", required = false) String reference) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return paystack.bookingStatus(tenant.id(), bookingId, customerEmail, reference);
  }

  /** Legacy only: proof for an EFT booking made before Manual EFT was removed. */
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

  @GetMapping("/bookings/{bookingId}")
  public Map<String, Object> lookupBooking(
      @PathVariable String merchantSlug,
      @PathVariable String bookingId,
      @RequestParam("customerEmail") String customerEmail) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    salonAccess.requireSalonShop(tenant.id());
    return salon.lookupPublic(tenant.id(), bookingId, customerEmail);
  }
}

