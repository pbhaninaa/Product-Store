package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.ReferralEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.ReferralRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralService {
  private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.15");

  private final ReferralRepository referrals;
  private final UserRepository users;
  private final MembershipRepository memberships;

  public ReferralService(
      ReferralRepository referrals, UserRepository users, MembershipRepository memberships) {
    this.referrals = referrals;
    this.users = users;
    this.memberships = memberships;
  }

  public String generateReferralCode(String email) {
    String code;
    int attempts = 0;
    do {
      code = generateCode(email, attempts);
      attempts++;
    } while (users.findByReferralCodeIgnoreCase(code).isPresent() && attempts < 10);
    if (attempts >= 10) {
      throw new IllegalStateException("Failed to generate unique referral code");
    }
    return code;
  }

  public String ensureReferralCode(UserEntity user) {
    if (user.referralCode != null && !user.referralCode.isBlank()) {
      return user.referralCode;
    }
    user.referralCode = generateReferralCode(user.email);
    users.save(user);
    return user.referralCode;
  }

  public Optional<UserEntity> resolveReferrer(String invitedBy) {
    String key = blankToNull(invitedBy);
    if (key == null) {
      return Optional.empty();
    }
    Optional<UserEntity> byEmail = users.findByEmailIgnoreCase(key);
    if (byEmail.isPresent()) {
      return byEmail;
    }
    return users.findByReferralCodeIgnoreCase(key);
  }

  @Transactional
  public ReferralEntity createReferral(String referralCode, UserEntity referee) {
    UserEntity referrer =
        resolveReferrer(referralCode).orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
    if (referee == null || referee.id == null) {
      throw new IllegalArgumentException("Referee profile is required");
    }
    if (referrer.id.equals(referee.id)
        || (referrer.email != null
            && referee.email != null
            && referrer.email.equalsIgnoreCase(referee.email))) {
      throw new IllegalArgumentException("You cannot refer yourself");
    }
    Optional<ReferralEntity> existing = referrals.findByRefereeId(referee.id);
    if (existing.isPresent()) {
      return existing.get();
    }
    ReferralEntity referral = new ReferralEntity();
    referral.referrerId = referrer.id;
    referral.refereeId = referee.id;
    var roles = memberships.findAllByUserId(referee.id);
    String roleName =
        roles.stream()
            .filter(m -> m.role == Role.MERCHANT_OWNER)
            .map(m -> m.role.name())
            .findFirst()
            .orElseGet(
                () ->
                    roles.isEmpty()
                        ? Role.CLIENT.name()
                        : roles.get(0).role.name());
    referral.refereeRole = roleName;
    referral.hasSubscribed = false;
    referral.commissionPaid = false;
    return referrals.save(referral);
  }

  @Transactional
  public ReferralEntity claimReferral(String invitedBy, UserEntity referee) {
    String key = blankToNull(invitedBy);
    if (key == null) {
      throw new IllegalArgumentException("Invite reference is required");
    }
    return createReferral(key, referee);
  }

  /**
   * 15% of the first paid merchant subscription, matching Wheel Hub provider billing.
   */
  @Transactional
  public void processMerchantSubscription(UUID tenantId, MerchantSubscriptionEntity subscription, BigDecimal fee) {
    if (tenantId == null || subscription == null) {
      return;
    }
    var owners = memberships.findAllByTenantIdAndRole(tenantId, Role.MERCHANT_OWNER);
    if (owners.isEmpty()) {
      return;
    }
    UUID refereeId = owners.get(0).userId;
    Optional<ReferralEntity> referralOpt = referrals.findByRefereeId(refereeId);
    if (referralOpt.isEmpty()) {
      return;
    }
    ReferralEntity referral = referralOpt.get();
    if (referral.hasSubscribed) {
      return;
    }
    if (!Role.MERCHANT_OWNER.name().equals(referral.refereeRole)) {
      return;
    }
    if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    BigDecimal commission = fee.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
    referral.hasSubscribed = true;
    referral.firstSubscriptionId = subscription.id;
    referral.subscribedAt = Instant.now();
    referral.commissionAmount = commission;
    referrals.save(referral);
  }

  @Transactional
  public ReferralEntity markCommissionPaid(UUID referralId, String notes) {
    ReferralEntity referral =
        referrals.findById(referralId).orElseThrow(() -> new IllegalArgumentException("Referral not found"));
    if (referral.commissionPaid) {
      throw new IllegalStateException("Commission already paid");
    }
    if (!referral.hasSubscribed) {
      throw new IllegalStateException("Referee has not subscribed yet");
    }
    referral.commissionPaid = true;
    referral.commissionPaidAt = Instant.now();
    referral.commissionNotes = notes;
    return referrals.save(referral);
  }

  public Page<ReferralEntity> getReferralsByReferrer(UUID referrerId, Pageable pageable) {
    return referrals.findByReferrerId(referrerId, pageable);
  }

  public Page<ReferralEntity> getAll(Pageable pageable) {
    return referrals.findAll(pageable);
  }

  public Map<String, Object> toMap(ReferralEntity referral) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", referral.id.toString());
    m.put("referrerId", referral.referrerId.toString());
    m.put("refereeId", referral.refereeId.toString());
    m.put("refereeRole", referral.refereeRole);
    m.put("hasSubscribed", referral.hasSubscribed);
    m.put(
        "firstSubscriptionId",
        referral.firstSubscriptionId != null ? referral.firstSubscriptionId.toString() : null);
    m.put("subscribedAt", referral.subscribedAt != null ? referral.subscribedAt.toString() : null);
    m.put("commissionAmount", referral.commissionAmount);
    m.put("commissionPaid", referral.commissionPaid);
    m.put(
        "commissionPaidAt",
        referral.commissionPaidAt != null ? referral.commissionPaidAt.toString() : null);
    m.put("commissionNotes", referral.commissionNotes);
    m.put("createdAt", referral.createdAt != null ? referral.createdAt.toString() : null);
    users
        .findById(referral.referrerId)
        .ifPresent(
            u -> {
              m.put("referrerEmail", u.email);
              m.put("referrerName", u.displayName == null ? "" : u.displayName);
            });
    users
        .findById(referral.refereeId)
        .ifPresent(
            u -> {
              m.put("refereeEmail", u.email);
              m.put("refereeName", u.displayName == null ? "" : u.displayName);
            });
    return m;
  }

  public Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("totalReferrals", referrals.count());
    m.put("subscribedCount", referrals.countByHasSubscribed(true));
    m.put("paidCommissions", referrals.countByCommissionPaid(true));
    m.put("pendingCommissions", referrals.countByHasSubscribedAndCommissionPaid(true, false));
    return m;
  }

  private String generateCode(String email, int attempt) {
    String base = email == null ? "USER" : email.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    if (base.length() > 6) {
      base = base.substring(0, 6);
    }
    int randomNum = 1000 + new Random().nextInt(9000) + attempt;
    return base + randomNum;
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
