package com.productstore.platform.controllers;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.services.PlatformCommissionService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.SupportAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.util.RefCodes;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider payouts and the platform commission (plan §4, endpoints 1-7). Platform admin only: every method
 * checks it first (support staff get 403; merchants and clients are stopped by the /api/support matcher).
 */
@RestController
@RequestMapping("/api/support")
public class SupportPayoutController {
  private final SupportAccessService access;
  private final PlatformCommissionService commission;
  private final ProviderPayoutService payouts;

  public SupportPayoutController(
      SupportAccessService access, PlatformCommissionService commission, ProviderPayoutService payouts) {
    this.access = access;
    this.commission = commission;
    this.payouts = payouts;
  }

  @GetMapping("/platform-commission")
  public PayoutDtos.Commission getCommission(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePlatformAdmin(principal);
    return commission.get();
  }

  @PutMapping("/platform-commission")
  public PayoutDtos.Commission setCommission(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    access.requirePlatformAdmin(principal);
    return commission.set(body == null ? null : body.get("commissionPercent"), principal);
  }

  @GetMapping("/payouts/owed")
  public PayoutDtos.OwedList owed(@AuthenticationPrincipal ApiUserPrincipal principal) {
    access.requirePlatformAdmin(principal);
    return payouts.owedList();
  }

  @GetMapping("/payouts/owed/{tenantId}")
  public PayoutDtos.ReleasePreview releasePreview(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable String tenantId) {
    access.requirePlatformAdmin(principal);
    return payouts.preview(RefCodes.parseUuidOrNull(tenantId));
  }

  @PostMapping("/payouts")
  public PayoutDtos.PayoutDetail release(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    access.requirePlatformAdmin(principal);
    return payouts.release(releaseRequest(body == null ? Map.of() : body), principal);
  }

  @GetMapping("/payouts")
  public PayoutDtos.PayoutPage history(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(required = false) String tenantId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    access.requirePlatformAdmin(principal);
    UUID tenant = null;
    if (tenantId != null && !tenantId.isBlank()) {
      tenant = RefCodes.parseUuidOrNull(tenantId);
      if (tenant == null) throw new IllegalArgumentException("validation_error");
    }
    return payouts.history(tenant, startDate, endDate, page, size);
  }

  @GetMapping("/payouts/{payoutId}")
  public PayoutDtos.PayoutDetail payout(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable String payoutId) {
    access.requirePlatformAdmin(principal);
    return payouts.detail(RefCodes.parseUuidOrNull(payoutId));
  }

  /** Reads the release body; field rules (reference, note, fee >= 0) are checked by the service. */
  static ProviderPayoutService.ReleaseRequest releaseRequest(Map<String, Object> body) {
    Object rawTenant = body.get("tenantId");
    UUID tenantId = rawTenant == null ? null : RefCodes.parseUuidOrNull(String.valueOf(rawTenant));
    if (rawTenant != null && tenantId == null) throw new IllegalArgumentException("validation_error");

    List<UUID> attemptIds = new ArrayList<>();
    Object rawIds = body.get("attemptIds");
    if (rawIds instanceof List<?> list) {
      for (Object o : list) {
        UUID id = o == null ? null : RefCodes.parseUuidOrNull(String.valueOf(o));
        if (id == null) throw new IllegalArgumentException("validation_error");
        attemptIds.add(id);
      }
    } else if (rawIds != null) {
      throw new IllegalArgumentException("validation_error");
    }

    Object version = body.get("payableVersion");
    return new ProviderPayoutService.ReleaseRequest(
        tenantId,
        attemptIds,
        version == null ? null : String.valueOf(version),
        feeTotalCents(body.get("feeTotalCents")),
        body.get("reference") == null ? null : String.valueOf(body.get("reference")),
        body.get("note") == null ? null : String.valueOf(body.get("note")));
  }

  /** Absent or null means "use the captured fees"; anything but a whole number of cents is invalid. */
  private static Long feeTotalCents(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Integer || raw instanceof Long || raw instanceof Short) {
      return ((Number) raw).longValue();
    }
    if (raw instanceof BigInteger b && b.bitLength() < 64) {
      return b.longValue();
    }
    throw new IllegalArgumentException("payout_fee_invalid");
  }
}
