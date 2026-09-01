package com.productstore.platform.controllers;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.PayFastPaymentService;
import com.productstore.platform.services.PeachPaymentService;
import com.productstore.platform.services.TenantAccessService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PeachController {
  private final PeachPaymentService peachPaymentService;
  private final PayFastPaymentService payFastPaymentService;
  private final TenantAccessService tenantAccess;
  private final String frontendBaseUrl;

  public PeachController(
      PeachPaymentService peachPaymentService,
      PayFastPaymentService payFastPaymentService,
      TenantAccessService tenantAccess,
      @Value("${app.frontend-base-url:http://localhost:8085}") String frontendBaseUrl) {
    this.peachPaymentService = peachPaymentService;
    this.payFastPaymentService = payFastPaymentService;
    this.tenantAccess = tenantAccess;
    this.frontendBaseUrl = trimSlash(frontendBaseUrl);
  }

  @GetMapping("/peach/configured")
  public Map<String, Object> configured() {
    return Map.of(
        "configured",
        peachPaymentService.isPlatformConfigured() || payFastPaymentService.isPlatformConfigured(),
        "payfastConfigured",
        payFastPaymentService.isPlatformConfigured());
  }

  /**
   * Peach webhook / shopper result notification — publicly reachable.
   */
  @PostMapping(value = "/peach/webhook", consumes = {"application/x-www-form-urlencoded", "application/json"})
  public ResponseEntity<String> webhook(
      HttpServletRequest request, @RequestBody(required = false) Map<String, Object> jsonBody) {
    try {
      peachPaymentService.handleWebhook(readParams(request, jsonBody));
      return ResponseEntity.ok("OK");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("INVALID");
    }
  }

  /**
   * Peach returns shoppers with a POST. Process that signed response on the backend, then issue a
   * browser-friendly redirect to the SPA.
   */
  @PostMapping(value = "/peach/return", consumes = {"application/x-www-form-urlencoded", "application/json"})
  public ResponseEntity<Void> shopperReturn(
      HttpServletRequest request,
      @RequestParam("returnPath") String returnPath,
      @RequestBody(required = false) Map<String, Object> jsonBody) {
    if (!isSafeReturnPath(returnPath)) {
      return ResponseEntity.badRequest().build();
    }
    try {
      peachPaymentService.handleWebhook(readParams(request, jsonBody));
      return ResponseEntity.status(HttpStatus.SEE_OTHER)
          .location(URI.create(frontendBaseUrl + returnPath))
          .build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
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

  private static Map<String, String> readParams(
      HttpServletRequest request, Map<String, Object> jsonBody) {
    Map<String, String> params = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (!"returnPath".equals(key)
                  && values != null
                  && values.length > 0
                  && values[0] != null) {
                params.put(key, values[0]);
              }
            });
    if (jsonBody != null) {
      jsonBody.forEach(
          (key, value) -> {
            if (value != null) {
              params.put(key, String.valueOf(value));
            }
          });
    }
    return params;
  }

  private static boolean isSafeReturnPath(String path) {
    return path != null
        && path.startsWith("/m/")
        && !path.startsWith("//")
        && !path.contains("://")
        && !path.contains("\\");
  }

  private static String trimSlash(String value) {
    String out = value == null ? "" : value.trim();
    while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
    return out;
  }
}
