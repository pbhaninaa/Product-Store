package com.productstore.platform.controllers;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.ReferralService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {
  private final ReferralService referrals;
  private final UserRepository users;

  public ReferralController(ReferralService referrals, UserRepository users) {
    this.referrals = referrals;
    this.users = users;
  }

  public record ClaimReferralRequest(String invitedBy, String referralCode) {
    String resolveInviteKey() {
      if (invitedBy != null && !invitedBy.isBlank()) return invitedBy.trim();
      if (referralCode != null && !referralCode.isBlank()) return referralCode.trim();
      return null;
    }
  }

  public record MarkCommissionPaidRequest(UUID referralId, String notes) {}

  @GetMapping("/my-code")
  public Map<String, String> myCode(@AuthenticationPrincipal ApiUserPrincipal principal) {
    UserEntity user = requireUser(principal);
    return Map.of("referralCode", referrals.ensureReferralCode(user));
  }

  @GetMapping("/my-referrals")
  public Map<String, Object> myReferrals(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    UserEntity user = requireUser(principal);
    var result = referrals.getReferralsByReferrer(user.id, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    return Map.of(
        "content", result.map(referrals::toMap).getContent(),
        "totalElements", result.getTotalElements(),
        "totalPages", result.getTotalPages(),
        "number", result.getNumber());
  }

  @PostMapping("/claim")
  public Map<String, Object> claim(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody ClaimReferralRequest request) {
    UserEntity user = requireUser(principal);
    String inviteKey = request != null ? request.resolveInviteKey() : null;
    if (inviteKey == null) throw new IllegalArgumentException("Invite reference is required");
    return referrals.toMap(referrals.claimReferral(inviteKey, user));
  }

  @GetMapping("/admin/all")
  public Map<String, Object> adminAll(
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireSupport(principal);
    var result = referrals.getAll(PageRequest.of(page, Math.min(Math.max(size, 1), 500)));
    return Map.of(
        "content", result.map(referrals::toMap).getContent(),
        "totalElements", result.getTotalElements(),
        "totalPages", result.getTotalPages(),
        "number", result.getNumber());
  }

  @GetMapping("/admin/stats")
  public Map<String, Object> adminStats(@AuthenticationPrincipal ApiUserPrincipal principal) {
    requireSupport(principal);
    return referrals.stats();
  }

  @PostMapping("/admin/mark-paid")
  public Map<String, Object> markPaid(
      @AuthenticationPrincipal ApiUserPrincipal principal, @RequestBody MarkCommissionPaidRequest request) {
    requireSupport(principal);
    if (request == null || request.referralId() == null) {
      throw new IllegalArgumentException("referralId is required");
    }
    return referrals.toMap(referrals.markCommissionPaid(request.referralId(), request.notes()));
  }

  private UserEntity requireUser(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    return users.findById(principal.userId()).orElseThrow(() -> new IllegalArgumentException("not_authenticated"));
  }

  private static void requireSupport(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    boolean ok =
        principal.roles().stream().anyMatch(r -> r == Role.SUPPORT_USER || r == Role.PLATFORM_ADMIN);
    if (!ok) throw new IllegalArgumentException("forbidden");
  }
}
