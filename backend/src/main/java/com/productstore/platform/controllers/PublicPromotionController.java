package com.productstore.platform.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.PromotionService;
import com.productstore.platform.services.SalonBookingService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/promotions")
public class PublicPromotionController {
  private final TenantAccessService tenantAccess;
  private final PromotionService promotions;
  private final CheckoutService checkout;
  private final SalonBookingService salon;

  public PublicPromotionController(
      TenantAccessService tenantAccess,
      PromotionService promotions,
      CheckoutService checkout,
      SalonBookingService salon) {
    this.tenantAccess = tenantAccess;
    this.promotions = promotions;
    this.checkout = checkout;
    this.salon = salon;
  }

  @GetMapping
  public List<Map<String, Object>> listActive(@PathVariable String merchantSlug) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return promotions.toMaps(promotions.listActiveToday(tenant.id()));
  }

  public record QuoteLine(UUID product_id, Integer quantity) {}

  /** Cart lines for a product checkout, or a serviceId for a salon booking. */
  public record QuoteRequest(List<QuoteLine> items, UUID serviceId) {}

  /**
   * Best currently-valid promotion for a cart or booking, priced on the server. The storefront shows
   * the discount line from this and sends the returned promoId with the order; checkout re-validates it.
   */
  @PostMapping("/quote")
  public Map<String, Object> quote(
      @PathVariable String merchantSlug, @RequestBody(required = false) QuoteRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    if (req != null && req.serviceId() != null) {
      return salon.quoteBookingPromotion(tenant.id(), req.serviceId());
    }
    List<CheckoutService.CreateOrderLine> lines = new ArrayList<>();
    if (req != null && req.items() != null) {
      for (QuoteLine l : req.items()) {
        if (l == null || l.product_id() == null || l.quantity() == null) continue;
        lines.add(new CheckoutService.CreateOrderLine(l.product_id(), l.quantity()));
      }
    }
    return checkout.quotePromotion(tenant.id(), lines);
  }
}
