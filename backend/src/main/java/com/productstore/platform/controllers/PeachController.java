package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.PeachPaymentService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Card payments moved from Peach to PayFast. The Peach-specific routes ({@code /peach/configured},
 * {@code /peach/webhook}, {@code /peach/return}) are retired and answer 410 Gone so a forged or
 * replayed Peach notification can no longer mark orders, bookings or subscriptions as paid.
 *
 * <p>The order/booking payment-status lookups stay: they are provider-neutral (scoped to the
 * tenant and the customer's email) and the web PayFast return page and the mobile app still call
 * the legacy {@code peach-status} path.
 */
@RestController
@RequestMapping("/api/public")
public class PeachController {
  private final PeachPaymentService peachPaymentService;
  private final TenantAccessService tenantAccess;

  public PeachController(PeachPaymentService peachPaymentService, TenantAccessService tenantAccess) {
    this.peachPaymentService = peachPaymentService;
    this.tenantAccess = tenantAccess;
  }

  @RequestMapping({"/peach/configured", "/peach/webhook", "/peach/return"})
  public ResponseEntity<Map<String, Object>> retiredPeachRoute() {
    return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "peach_retired"));
  }

  @GetMapping("/m/{merchantSlug}/checkout/orders/{orderId}/peach-status")
  public Map<String, Object> orderStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @RequestParam("customerEmail") String customerEmail) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return peachPaymentService.orderStatus(tenant.id(), orderId, customerEmail);
  }

  @GetMapping("/m/{merchantSlug}/salon/bookings/{bookingId}/peach-status")
  public Map<String, Object> bookingStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @RequestParam("customerEmail") String customerEmail) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return peachPaymentService.bookingStatus(tenant.id(), bookingId, customerEmail);
  }

  @GetMapping("/m/{merchantSlug}/checkout/orders/{orderId}/payfast-status")
  public Map<String, Object> orderPayFastStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @RequestParam("customerEmail") String customerEmail) {
    return orderStatus(merchantSlug, orderId, customerEmail);
  }

  @GetMapping("/m/{merchantSlug}/salon/bookings/{bookingId}/payfast-status")
  public Map<String, Object> bookingPayFastStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID bookingId,
      @RequestParam("customerEmail") String customerEmail) {
    return bookingStatus(merchantSlug, bookingId, customerEmail);
  }
}
