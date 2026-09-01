package com.productstore.platform.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.config.PayFastProperties;
import com.productstore.platform.config.PeachProperties;
import com.productstore.platform.constants.SubscriptionPaymentProofStatus;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.PlatformBankingEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.PlatformBankingRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.repositories.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MerchantSubscriptionService {
  private static final Logger log = LoggerFactory.getLogger(MerchantSubscriptionService.class);
  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
  private static final DateTimeFormatter REF_TS = DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZONE);
  /** One-time free trial length for new merchants (UTC). */
  public static final int TRIAL_DAYS = 7;
  /** Full entitlement while the durable trial window is active. */
  private static final TenantEntity.SubscriptionPlan TRIAL_ENTITLEMENT =
      TenantEntity.SubscriptionPlan.PREMIUM;

  private final MerchantSubscriptionRepository subscriptions;
  private final SubscriptionPlanPricingRepository plans;
  private final PlatformBankingRepository banking;
  private final TenantRepository tenants;
  private final EmployeeRepository employees;
  private final ProductRepository products;
  private final EftProofDocumentAnalyzer eftProofAnalyzer;
  private final InAppNotificationService inAppNotifications;
  private final PeachProperties peachProperties;
  private final PayFastProperties payFastProperties;
  private final ReferralService referralService;
  private final String uploadsDir;
  private final Clock clock;

  public MerchantSubscriptionService(
      MerchantSubscriptionRepository subscriptions,
      SubscriptionPlanPricingRepository plans,
      PlatformBankingRepository banking,
      TenantRepository tenants,
      EmployeeRepository employees,
      ProductRepository products,
      EftProofDocumentAnalyzer eftProofAnalyzer,
      InAppNotificationService inAppNotifications,
      PeachProperties peachProperties,
      PayFastProperties payFastProperties,
      ReferralService referralService,
      @Value("${app.uploads.dir:./data/uploads}") String uploadsDir,
      ObjectProvider<Clock> clockProvider) {
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.banking = banking;
    this.tenants = tenants;
    this.employees = employees;
    this.products = products;
    this.eftProofAnalyzer = eftProofAnalyzer;
    this.inAppNotifications = inAppNotifications;
    this.peachProperties = peachProperties;
    this.payFastProperties = payFastProperties;
    this.referralService = referralService;
    this.uploadsDir = uploadsDir;
    this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
  }

  public List<Map<String, Object>> listPlans() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (TenantEntity.SubscriptionPlan tier : TenantEntity.SubscriptionPlan.values()) {
      plans.findByTier(tier).ifPresent(p -> out.add(planToMap(p)));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> updatePlan(TenantEntity.SubscriptionPlan tier, Map<String, Object> body) {
    if (tier == null) throw new IllegalArgumentException("tier_required");
    SubscriptionPlanPricingEntity p =
        plans.findByTier(tier).orElseThrow(() -> new IllegalArgumentException("plan_not_found"));
    if (body.get("subscriptionFee") != null) {
      p.subscriptionFee = Double.parseDouble(String.valueOf(body.get("subscriptionFee")));
    }
    if (body.get("billingPeriodDays") != null) {
      p.billingPeriodDays = Math.max(1, Integer.parseInt(String.valueOf(body.get("billingPeriodDays"))));
    }
    if (body.get("featureInsights") != null) {
      p.featureInsights = Boolean.parseBoolean(String.valueOf(body.get("featureInsights")));
    }
    if (body.get("featureEmailAlerts") != null) {
      p.featureEmailAlerts = Boolean.parseBoolean(String.valueOf(body.get("featureEmailAlerts")));
    }
    if (body.get("featureWhatsapp") != null) {
      p.featureWhatsapp = Boolean.parseBoolean(String.valueOf(body.get("featureWhatsapp")));
    }
    if (body.get("featurePayroll") != null) {
      p.featurePayroll = Boolean.parseBoolean(String.valueOf(body.get("featurePayroll")));
    }
    if (body.get("maxEmployees") != null) {
      p.maxEmployees = Integer.parseInt(String.valueOf(body.get("maxEmployees")));
    }
    if (body.get("maxProducts") != null) {
      p.maxProducts = Integer.parseInt(String.valueOf(body.get("maxProducts")));
    }
    plans.save(p);
    return planToMap(p);
  }

  @Transactional
  public List<Map<String, Object>> listMerchantSubscriptions() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (TenantEntity t : tenants.findAll()) {
      MerchantSubscriptionEntity sub = ensureSubscriptionRow(t.id);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("tenantId", t.id.toString());
      row.put("slug", t.slug);
      row.put("name", t.name);
      row.put("planTier", sub.planTier != null ? sub.planTier.name() : null);
      row.put("billedPlanTier", sub.billedPlanTier != null ? sub.billedPlanTier.name() : null);
      row.put("active", sub.active);
      row.put("valid", isSubscriptionValid(sub));
      row.put("onTrial", isOnTrial(sub));
      row.put("trialEndAt", sub.trialEndAt != null ? sub.trialEndAt.toString() : null);
      row.put("daysRemaining", daysRemainingOnTrial(sub));
      row.put("periodEnd", sub.periodEnd != null ? sub.periodEnd.toString() : null);
      row.put(
          "paymentProofStatus",
          sub.paymentProofStatus != null ? sub.paymentProofStatus.name() : "NONE");
      row.put(
          "peachPaymentMethod",
          sub.peachPaymentMethod != null ? sub.peachPaymentMethod.name() : "");
      out.add(row);
    }
    return out;
  }

  @Transactional
  public Map<String, Object> buildStatus(UUID tenantId) {
    ensureSubscriptionRow(tenantId);
    return buildStatusSnapshot(tenantId);
  }

  private Map<String, Object> buildStatusSnapshot(UUID tenantId) {
    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    syncOnTrialFlag(sub);
    SubscriptionPlanPricingEntity billingPreview =
        sub.planTier != null
            ? plans.findByTier(sub.planTier).orElse(null)
            : plans.findByTier(TenantEntity.SubscriptionPlan.STARTER).orElse(null);
    TenantEntity.SubscriptionPlan entitlementTier = effectiveEntitlementTier(sub);
    SubscriptionPlanPricingEntity entitlementPlan =
        entitlementTier != null ? plans.findByTier(entitlementTier).orElse(null) : null;

    double fee = billingPreview != null ? billingPreview.subscriptionFee : 0;
    boolean valid = isSubscriptionValid(sub);
    boolean onTrial = isOnTrial(sub);
    boolean trialExpired = isTrialExpired(sub);
    // Plan choice is never required to start the trial; trialEligible stays false.
    boolean trialEligible = false;
    double amountDue = onTrial ? 0d : fee;

    Map<String, Object> features = new LinkedHashMap<>();
    if (entitlementPlan != null && valid) {
      features.put("insights", entitlementPlan.featureInsights);
      features.put("emailAlerts", entitlementPlan.featureEmailAlerts);
      features.put("whatsapp", entitlementPlan.featureWhatsapp);
      features.put("payroll", entitlementPlan.featurePayroll);
    } else {
      features.put("insights", false);
      features.put("emailAlerts", false);
      features.put("whatsapp", false);
      features.put("payroll", false);
    }

    SubscriptionPaymentProofStatus ps =
        sub.paymentProofStatus != null ? sub.paymentProofStatus : SubscriptionPaymentProofStatus.NONE;
    boolean proofClear =
        ps == SubscriptionPaymentProofStatus.NONE || ps == SubscriptionPaymentProofStatus.REJECTED;
    boolean needsForInactive = sub.planTier != null && !valid && proofClear && trialExpired;
    boolean needsForUpgrade =
        sub.planTier != null
            && valid
            && !onTrial
            && sub.billedPlanTier != null
            && !sub.planTier.equals(sub.billedPlanTier)
            && proofClear;

    if ((needsForInactive || needsForUpgrade)
        && (sub.mandatoryPaymentReference == null || sub.mandatoryPaymentReference.isBlank())) {
      regenerateMandatoryPaymentReference(sub, tenant);
      sub = subscriptions.save(sub);
    }

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("planTier", sub.planTier != null ? sub.planTier.name() : null);
    m.put("billedPlanTier", sub.billedPlanTier != null ? sub.billedPlanTier.name() : null);
    m.put("entitlementTier", entitlementTier != null ? entitlementTier.name() : null);
    m.put("active", sub.active);
    m.put("valid", valid);
    m.put("periodStart", sub.periodStart != null ? sub.periodStart.toString() : null);
    m.put("periodEnd", sub.periodEnd != null ? sub.periodEnd.toString() : null);
    m.put("trialStartAt", sub.trialStartAt != null ? sub.trialStartAt.toString() : null);
    m.put("trialEndAt", sub.trialEndAt != null ? sub.trialEndAt.toString() : null);
    m.put("daysRemaining", daysRemainingOnTrial(sub));
    m.put("trialExpired", trialExpired);
    m.put("subscriptionFee", fee);
    m.put("grandTotalDue", round2(amountDue));
    m.put("amountDueThisPeriod", round2(amountDue));
    m.put("billingPeriodDays", billingPreview != null ? billingPreview.billingPeriodDays : 30);
    m.put("trialEligible", trialEligible);
    m.put("trialUsed", sub.trialUsed);
    m.put("onTrial", onTrial);
    m.put("maxEmployees", entitlementPlan != null ? entitlementPlan.maxEmployees : null);
    m.put("maxProducts", entitlementPlan != null ? entitlementPlan.maxProducts : null);
    m.put("features", features);
    m.put("needsPlanSelection", !onTrial && sub.planTier == null);
    m.put("paymentProofStatus", ps.name());
    m.put(
        "paymentProofUploadedAt",
        sub.paymentProofUploadedAt != null ? sub.paymentProofUploadedAt.toString() : null);
    m.put("paymentProofOriginalFilename", sub.paymentProofOriginalFilename);
    m.put("paymentProofRejectionNote", sub.paymentProofRejectionNote);
    m.put("paymentProofExpectedFee", sub.paymentProofExpectedFee);
    m.put("paymentProofAutoPassed", sub.paymentProofAutoPassed);
    m.put("paymentProofAutoSummary", sub.paymentProofAutoSummary);
    m.put("mandatoryPaymentReference", sub.mandatoryPaymentReference);
    m.put(
        "paymentReferenceGeneratedAt",
        sub.paymentReferenceGeneratedAt != null ? sub.paymentReferenceGeneratedAt.toString() : null);
    m.put("needsPaymentProofUpload", needsForInactive || needsForUpgrade);
    m.put("needsPayment", needsForInactive || needsForUpgrade);
    m.put("paymentProofPendingReview", ps == SubscriptionPaymentProofStatus.PENDING);
    m.put("platformBankingConfigured", isBankingConfigured(ensureBanking()));
    m.put("peachConfigured", payFastProperties.isConfigured() || peachProperties.isConfigured());
    m.put("payfastConfigured", payFastProperties.isConfigured());
    m.put(
        "peachPaymentMethod",
        sub.peachPaymentMethod != null ? sub.peachPaymentMethod.name() : "");
    m.put("tenantSlug", tenant.slug);
    m.put("tenantName", tenant.name);
    return m;
  }

  @Transactional
  public Map<String, Object> choosePlan(UUID tenantId, TenantEntity.SubscriptionPlan tier) {
    if (tier == null) throw new IllegalArgumentException("tier_required");
    plans.findByTier(tier).orElseThrow(() -> new IllegalArgumentException("invalid_plan_tier"));
    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    syncOnTrialFlag(sub);

    boolean wasValid = isSubscriptionValid(sub);
    boolean freeChangeDuringTrial = wasValid && isOnTrial(sub);

    TenantEntity.SubscriptionPlan previous = sub.planTier;
    sub.planTier = tier;
    tenant.subscriptionPlan = tier;
    tenants.save(tenant);

    if (freeChangeDuringTrial) {
      if (previous != null && previous != tier) {
        clearPaymentProof(sub);
        sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
        sub.paymentProofAutoSummary =
            "Preferred plan saved during free trial — Peach payment is only required after the trial ends.";
      }
      // Do not overwrite billed entitlement during trial; trial grants PREMIUM until expiry.
      subscriptions.save(sub);
      inAppNotifications.notifyTenantStaff(
          tenantId,
          "Plan preference saved",
          "Your preferred plan after the free trial is "
              + tier.name()
              + ". Full Premium access continues until "
              + (sub.trialEndAt != null ? sub.trialEndAt : "trial end")
              + ".",
          "SUBSCRIPTION_ACTIVATED",
          "SUBSCRIPTION",
          tenantId.toString());
      return buildStatusSnapshot(tenantId);
    }

    if (previous != null && previous != tier) {
      clearPaymentProof(sub);
    }
    if (previous == null || previous != tier) {
      regenerateMandatoryPaymentReference(sub, tenant);
      inAppNotifications.notifyTenantStaff(
          tenantId,
          "Complete your subscription payment",
          "Your plan was updated. Open Plan & billing to pay with Peach (card or Instant EFT).",
          "SUBSCRIPTION_ACTION_REQUIRED",
          "SUBSCRIPTION",
          tenantId.toString());
    }
    subscriptions.save(sub);
    return buildStatusSnapshot(tenantId);
  }

  @Transactional
  public Map<String, Object> uploadPaymentProof(UUID tenantId, MultipartFile file) throws IOException {
    throw new IllegalArgumentException("manual_eft_disabled");
  }

  /**
   * Activates a plan without Peach payment (demo bootstrap and integration tests only). Not exposed
   * via support HTTP APIs — those return 410.
   */
  @Transactional
  public Map<String, Object> forceActivatePlan(UUID tenantId, TenantEntity.SubscriptionPlan tier) {
    if (tier == null) throw new IllegalArgumentException("tier_required");
    plans.findByTier(tier).orElseThrow(() -> new IllegalArgumentException("invalid_plan_tier"));
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    sub.planTier = tier;
    clearPaymentProof(sub);
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
    sub.paymentProofReviewedAt = now();
    sub.trialUsed = true;
    sub.onTrial = false;
    subscriptions.save(sub);

    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    tenant.subscriptionPlan = tier;
    tenants.save(tenant);
    return activatePeriod(tenantId);
  }

  @Transactional
  public Map<String, Object> activatePeriod(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.planTier == null) {
      throw new IllegalStateException("select_plan_first");
    }
    SubscriptionPlanPricingEntity pricing =
        plans.findByTier(sub.planTier).orElseThrow(() -> new IllegalStateException("plan_missing"));
    LocalDate start = LocalDate.now(ZONE);
    LocalDate end = start.plusDays(Math.max(1, pricing.billingPeriodDays) - 1L);
    sub.active = true;
    sub.periodStart = start;
    sub.periodEnd = end;
    sub.billedPlanTier = sub.planTier;
    sub.onTrial = false;
    // Paid activation must never reset durable trial dates.
    subscriptions.save(sub);

    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    tenant.subscriptionPlan = sub.billedPlanTier;
    tenants.save(tenant);

    inAppNotifications.notifyTenantStaff(
        tenantId,
        "Subscription activated",
        "Your "
            + sub.billedPlanTier.name()
            + " plan is active until "
            + end
            + ".",
        "SUBSCRIPTION_ACTIVATED",
        "SUBSCRIPTION",
        tenantId.toString());
    return buildStatusSnapshot(tenantId);
  }

  @Transactional
  public Map<String, Object> approveProof(UUID tenantId) {
    throw new IllegalArgumentException("subscription_proof_mutation_disabled");
  }

  @Transactional
  public Map<String, Object> rejectProof(UUID tenantId, String note) {
    throw new IllegalArgumentException("subscription_proof_mutation_disabled");
  }

  public List<Map<String, Object>> listPendingProofs() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (MerchantSubscriptionEntity sub :
        subscriptions.findByPaymentProofStatus(SubscriptionPaymentProofStatus.PENDING)) {
      tenants
          .findById(sub.tenantId)
          .ifPresent(
              t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tenantId", t.id.toString());
                row.put("slug", t.slug);
                row.put("name", t.name);
                row.put("planTier", sub.planTier != null ? sub.planTier.name() : null);
                row.put(
                    "uploadedAt",
                    sub.paymentProofUploadedAt != null ? sub.paymentProofUploadedAt.toString() : null);
                row.put("originalFilename", sub.paymentProofOriginalFilename);
                row.put("expectedFee", sub.paymentProofExpectedFee);
                row.put("autoPassed", sub.paymentProofAutoPassed);
                row.put("autoSummary", sub.paymentProofAutoSummary);
                row.put("mandatoryPaymentReference", sub.mandatoryPaymentReference);
                out.add(row);
              });
    }
    return out;
  }

  public Map<String, Object> getPlatformBanking() {
    PlatformBankingEntity b = ensureBanking();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("bankName", nz(b.bankName));
    m.put("accountName", nz(b.accountName));
    m.put("accountNumber", nz(b.accountNumber));
    m.put("branchCode", nz(b.branchCode));
    m.put("referenceHint", nz(b.referenceHint));
    m.put("paymentLink", nz(b.paymentLink));
    m.put("configured", isBankingConfigured(b));
    return m;
  }

  @Transactional
  public Map<String, Object> updatePlatformBanking(Map<String, Object> body) {
    throw new IllegalArgumentException("platform_banking_mutation_disabled");
  }

  public boolean hasEffectiveSubscription(UUID tenantId) {
    return isSubscriptionValid(ensureSubscriptionRow(tenantId));
  }

  /** True while a valid, active plan differs from the billed plan (mid-period upgrade awaiting payment). */
  public boolean hasPendingUpgrade(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    boolean valid = isSubscriptionValid(sub);
    return valid
        && sub.billedPlanTier != null
        && sub.planTier != null
        && !sub.planTier.equals(sub.billedPlanTier);
  }

  /**
   * Activates or renews the billing period after a verified Peach Hosted Checkout notification.
   * Idempotent while the subscription is already valid for the paid plan. Free-trial activation
   * remains a separate non-payment path.
   */
  @Transactional
  public void finalizePeachPaidSubscription(UUID tenantId, TenantEntity.SubscriptionPlan paidTier) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (paidTier != null) {
      sub.planTier = paidTier;
      TenantEntity tenant =
          tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
      tenant.subscriptionPlan = paidTier;
      tenants.save(tenant);
    }
    if (sub.planTier == null) {
      throw new IllegalStateException("select_plan_first");
    }
    boolean valid = isSubscriptionValid(sub);
    boolean upgradingWhileActive =
        valid
            && sub.billedPlanTier != null
            && !sub.planTier.equals(sub.billedPlanTier);
    if (valid && !upgradingWhileActive) {
      return;
    }
    clearPaymentProof(sub);
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
    sub.paymentProofReviewedAt = now();
    sub.paymentProofAutoPassed = true;
    sub.paymentProofAutoSummary = "Paid online via PayFast.";
    sub.onTrial = false;
    sub.trialUsed = true;
    subscriptions.save(sub);
    activatePeriod(tenantId);
    var pricing = plans.findByTier(sub.planTier).orElse(null);
    java.math.BigDecimal fee =
        pricing == null ? java.math.BigDecimal.ZERO : java.math.BigDecimal.valueOf(pricing.subscriptionFee);
    referralService.processMerchantSubscription(tenantId, sub, fee);
  }

  /** @deprecated Prefer {@link #finalizePeachPaidSubscription(UUID, TenantEntity.SubscriptionPlan)}. */
  @Transactional
  public void finalizePeachPaidSubscription(UUID tenantId) {
    finalizePeachPaidSubscription(tenantId, null);
  }

  public boolean grantsFeature(UUID tenantId, String feature) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (!isSubscriptionValid(sub)) return false;
    TenantEntity.SubscriptionPlan tier = effectiveEntitlementTier(sub);
    if (tier == null) return false;
    SubscriptionPlanPricingEntity p = plans.findByTier(tier).orElse(null);
    if (p == null) return false;
    return switch (feature) {
      case "insights" -> p.featureInsights;
      case "emailAlerts" -> p.featureEmailAlerts;
      case "whatsapp" -> p.featureWhatsapp;
      case "payroll" -> p.featurePayroll;
      default -> false;
    };
  }

  public void assertActiveSubscription(UUID tenantId) {
    if (!hasEffectiveSubscription(tenantId)) {
      throw new IllegalStateException("subscription_inactive");
    }
  }

  public void assertCanAddEmployee(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (!isSubscriptionValid(sub)) {
      throw new IllegalStateException("subscription_inactive");
    }
    TenantEntity.SubscriptionPlan tier = effectiveEntitlementTier(sub);
    SubscriptionPlanPricingEntity p =
        plans.findByTier(tier).orElseThrow(() -> new IllegalStateException("plan_missing"));
    if (p.maxEmployees < 0) return;
    long active = employees.findByTenantIdAndIsActive(tenantId, true).size();
    if (active >= p.maxEmployees) {
      throw new IllegalStateException("employee_limit_reached");
    }
  }

  public void assertCanUsePayroll(UUID tenantId) {
    if (!grantsFeature(tenantId, "payroll")) {
      throw new IllegalStateException("payroll_requires_plan");
    }
  }

  public void assertCanUseInsights(UUID tenantId) {
    if (!grantsFeature(tenantId, "insights")) {
      throw new IllegalStateException("insights_requires_plan");
    }
  }

  public void assertCanAddProduct(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (!isSubscriptionValid(sub)) {
      throw new IllegalStateException("subscription_inactive");
    }
    TenantEntity.SubscriptionPlan tier = effectiveEntitlementTier(sub);
    SubscriptionPlanPricingEntity p =
        plans.findByTier(tier).orElseThrow(() -> new IllegalStateException("plan_missing"));
    if (p.maxProducts < 0) return;
    long active = products.countActiveByTenant(tenantId);
    if (active >= p.maxProducts) {
      throw new IllegalStateException("product_limit_reached");
    }
  }

  /**
   * Creates the subscription row and starts the one-time 7-day free trial from merchant creation
   * (UTC). Full Premium entitlement until {@code trial_end_at}; no plan choice or payment required.
   */
  @Transactional
  public void provisionNewMerchantSubscription(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    if (sub.planTier == null) {
      sub.planTier = TenantEntity.SubscriptionPlan.STARTER;
    }
    if (tenant.subscriptionPlan == null) {
      tenant.subscriptionPlan = TenantEntity.SubscriptionPlan.STARTER;
      tenants.save(tenant);
    }
    Instant start =
        tenant.createdAt != null ? tenant.createdAt : now();
    applyDurableTrialWindow(sub, start);
    sub.trialUsed = true;
    sub.onTrial = true;
    sub.active = true;
    clearPaymentProof(sub);
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
    sub.paymentProofReviewedAt = now();
    sub.paymentProofAutoPassed = true;
    sub.paymentProofAutoSummary =
        "7-day free trial from store creation — full access, no payment required until trial ends.";
    regenerateMandatoryPaymentReference(sub, tenant);
    subscriptions.save(sub);
    inAppNotifications.notifyTenantStaff(
        tenantId,
        "Free trial started",
        "Your 7-day free trial is active with full Premium features until "
            + sub.trialEndAt
            + " (UTC). After that, renew with Peach (card or Instant EFT).",
        "SUBSCRIPTION_ACTIVATED",
        "SUBSCRIPTION",
        tenantId.toString());
  }

  public Path resolveProofFile(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.paymentProofRelativePath == null || sub.paymentProofRelativePath.isBlank()) {
      throw new IllegalArgumentException("proof_not_found");
    }
    String relative = sub.paymentProofRelativePath;
    for (Path root : proofStorageCandidates()) {
      Path file = root.resolve(relative).normalize();
      if (file.startsWith(root) && Files.isRegularFile(file)) {
        return file;
      }
    }
    Path uploadsRoot = Paths.get(uploadsDir).toAbsolutePath().normalize();
    Path legacy = uploadsRoot.resolve(relative).normalize();
    if (legacy.startsWith(uploadsRoot) && Files.isRegularFile(legacy)) {
      return legacy;
    }
    throw new IllegalArgumentException("proof_not_found");
  }

  private MerchantSubscriptionEntity ensureSubscriptionRow(UUID tenantId) {
    MerchantSubscriptionEntity sub =
        subscriptions
            .findByTenantId(tenantId)
            .orElseGet(
                () -> {
                  MerchantSubscriptionEntity s = new MerchantSubscriptionEntity();
                  s.tenantId = tenantId;
                  TenantEntity t = tenants.findById(tenantId).orElse(null);
                  if (t != null && t.subscriptionPlan != null) {
                    s.planTier = t.subscriptionPlan;
                  }
                  return subscriptions.save(s);
                });
    if (backfillTrialDatesOnce(sub)) {
      return subscriptions.save(sub);
    }
    syncOnTrialFlag(sub);
    return sub;
  }

  /**
   * One-time backfill of durable trial dates from tenant {@code createdAt}. Old accounts receive
   * historical dates (often already expired) — never a fresh 7-day window from now. Paid active
   * periods are left untouched.
   */
  private boolean backfillTrialDatesOnce(MerchantSubscriptionEntity sub) {
    if (sub.trialDatesBackfilled && sub.trialStartAt != null && sub.trialEndAt != null) {
      return false;
    }
    boolean changed = false;
    if (sub.trialStartAt == null || sub.trialEndAt == null) {
      TenantEntity t = tenants.findById(sub.tenantId).orElse(null);
      Instant created =
          t != null && t.createdAt != null ? t.createdAt : now().minus(TRIAL_DAYS, ChronoUnit.DAYS);
      applyDurableTrialWindow(sub, created);
      changed = true;
    } else if (!sub.trialDatesBackfilled) {
      sub.trialDatesBackfilled = true;
      changed = true;
    }
    // Historical trial is consumed; do not reissue.
    if (!sub.trialUsed) {
      sub.trialUsed = true;
      changed = true;
    }
    boolean paidValid = isPaidPeriodValid(sub);
    if (paidValid) {
      // Paid active subscriptions are unaffected aside from recording historical trial dates.
      if (sub.onTrial) {
        sub.onTrial = false;
        changed = true;
      }
    } else if (isWithinTrialWindow(sub, now())) {
      if (!sub.active) {
        sub.active = true;
        changed = true;
      }
      if (!sub.onTrial) {
        sub.onTrial = true;
        changed = true;
      }
    } else {
      if (sub.onTrial) {
        sub.onTrial = false;
        changed = true;
      }
    }
    return changed;
  }

  private void applyDurableTrialWindow(MerchantSubscriptionEntity sub, Instant start) {
    if (sub.trialStartAt != null && sub.trialEndAt != null) {
      sub.trialDatesBackfilled = true;
      return;
    }
    Instant trialStart = start != null ? start : now();
    sub.trialStartAt = trialStart;
    sub.trialEndAt = trialStart.plus(TRIAL_DAYS, ChronoUnit.DAYS);
    sub.trialDatesBackfilled = true;
  }

  private void syncOnTrialFlag(MerchantSubscriptionEntity sub) {
    boolean shouldBeOnTrial = isOnTrial(sub);
    if (sub.onTrial != shouldBeOnTrial) {
      sub.onTrial = shouldBeOnTrial;
      subscriptions.save(sub);
    }
  }

  /** Access is valid during the durable UTC trial window or an active paid billing period. */
  private boolean isSubscriptionValid(MerchantSubscriptionEntity sub) {
    if (sub == null) return false;
    if (isWithinTrialWindow(sub, now())) return true;
    return isPaidPeriodValid(sub);
  }

  private boolean isPaidPeriodValid(MerchantSubscriptionEntity sub) {
    if (sub == null || !sub.active || sub.periodStart == null || sub.periodEnd == null) return false;
    LocalDate today = LocalDate.now(ZONE);
    return !today.isBefore(sub.periodStart) && !today.isAfter(sub.periodEnd);
  }

  /** True while now is in [{@code trialStartAt}, {@code trialEndAt}) UTC and not on a paid period. */
  private boolean isOnTrial(MerchantSubscriptionEntity sub) {
    return isWithinTrialWindow(sub, now()) && !isPaidPeriodValid(sub);
  }

  private boolean isTrialExpired(MerchantSubscriptionEntity sub) {
    if (sub == null || sub.trialEndAt == null) return sub != null && sub.trialUsed;
    return !now().isBefore(sub.trialEndAt);
  }

  /** Inclusive start, exclusive end at {@code trialEndAt} (UTC Instant boundary). */
  public static boolean isWithinTrialWindow(MerchantSubscriptionEntity sub, Instant now) {
    if (sub == null || sub.trialStartAt == null || sub.trialEndAt == null || now == null) {
      return false;
    }
    return !now.isBefore(sub.trialStartAt) && now.isBefore(sub.trialEndAt);
  }

  private int daysRemainingOnTrial(MerchantSubscriptionEntity sub) {
    if (!isOnTrial(sub) || sub.trialEndAt == null) return 0;
    long seconds = ChronoUnit.SECONDS.between(now(), sub.trialEndAt);
    if (seconds <= 0) return 0;
    return (int) ((seconds + 86_399L) / 86_400L);
  }

  private Instant now() {
    return Instant.now(clock);
  }

  /** True when Peach checkout must wait until the free trial ends. */
  public boolean isBlockingTrialForCheckout(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    return isOnTrial(sub);
  }

  private TenantEntity.SubscriptionPlan effectiveEntitlementTier(MerchantSubscriptionEntity sub) {
    if (!isSubscriptionValid(sub)) return null;
    if (isOnTrial(sub)) return TRIAL_ENTITLEMENT;
    return sub.billedPlanTier != null ? sub.billedPlanTier : sub.planTier;
  }

  private void regenerateMandatoryPaymentReference(MerchantSubscriptionEntity sub, TenantEntity tenant) {
    Instant ts = now();
    String slug =
        tenant.slug == null
            ? "SHOP"
            : tenant.slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
    if (slug.length() > 8) slug = slug.substring(0, 8);
    if (slug.isEmpty()) slug = "SHOP";
    String id4 = tenant.id.toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    String ref = "PS-" + slug + "-" + REF_TS.format(ts) + "-" + id4;
    if (ref.length() > 64) ref = ref.substring(0, 64);
    sub.mandatoryPaymentReference = ref;
    sub.paymentReferenceGeneratedAt = ts;
  }

  private void clearPaymentProof(MerchantSubscriptionEntity sub) {
    deleteProofFile(sub.paymentProofRelativePath);
    sub.paymentProofRelativePath = null;
    sub.paymentProofOriginalFilename = null;
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.NONE;
    sub.paymentProofUploadedAt = null;
    sub.paymentProofReviewedAt = null;
    sub.paymentProofRejectionNote = null;
    sub.paymentProofExpectedFee = null;
    sub.paymentProofAutoPassed = null;
    sub.paymentProofAutoSummary = null;
  }

  private String storePdf(UUID tenantId, byte[] payload) throws IOException {
    String name = tenantId + "-" + UUID.randomUUID() + ".pdf";
    IOException last = null;
    for (Path root : proofStorageCandidates()) {
      try {
        Files.createDirectories(root);
        Path dest = root.resolve(name).normalize();
        if (!dest.startsWith(root)) {
          throw new IOException("path_escape");
        }
        Files.write(dest, payload);
        if (!Files.isRegularFile(dest) || Files.size(dest) != payload.length) {
          throw new IOException("write_verify_failed");
        }
        log.info("Stored subscription proof tenant={} path={}", tenantId, dest);
        return name;
      } catch (IOException e) {
        last = e;
        log.warn("Subscription proof storage candidate {} failed: {}", root, e.toString());
      }
    }
    throw new IOException(
        "No writable proof storage under " + uploadsDir, last == null ? new IOException("none") : last);
  }

  private void deleteProofFile(String relative) {
    if (relative == null || relative.isBlank()) return;
    for (Path root : proofStorageCandidates()) {
      try {
        Path file = root.resolve(relative).normalize();
        if (file.startsWith(root)) {
          Files.deleteIfExists(file);
        }
      } catch (Exception ignored) {
        // best effort
      }
    }
    try {
      Path publicRoot = Paths.get(uploadsDir).toAbsolutePath().normalize();
      Path legacy = publicRoot.resolve(relative).normalize();
      if (legacy.startsWith(publicRoot)) Files.deleteIfExists(legacy);
    } catch (Exception ignored) {
      // best effort
    }
  }

  /**
   * Writable candidates, preferred first: same volume as product uploads ({@code
   * uploads/_private/...}), then sibling {@code ../private/...}, then baked-in {@code
   * /app/data/...} paths used by the Docker image.
   */
  private List<Path> proofStorageCandidates() {
    LinkedHashSet<Path> out = new LinkedHashSet<>();
    Path uploads = Paths.get(uploadsDir).toAbsolutePath().normalize();
    out.add(uploads.resolve("_private").resolve("subscription-proofs"));
    Path parent = uploads.getParent();
    if (parent != null) {
      out.add(parent.resolve("private").resolve("subscription-proofs"));
    }
    out.add(Paths.get("/app/data/uploads/_private/subscription-proofs").toAbsolutePath().normalize());
    out.add(Paths.get("/app/data/private/subscription-proofs").toAbsolutePath().normalize());
    return List.copyOf(out);
  }

  private Path privateProofRoot() {
    return proofStorageCandidates().get(0);
  }

  private void logPendingProof(TenantEntity tenant, MerchantSubscriptionEntity sub) {
    log.warn(
        "Subscription payment proof pending review tenant={} slug={} plan={} ref={} fee={}",
        tenant.id,
        tenant.slug,
        sub.planTier,
        sub.mandatoryPaymentReference,
        sub.paymentProofExpectedFee);
    inAppNotifications.notifyPlatformStaff(
        "Subscription proof pending",
        tenant.name
            + " ("
            + tenant.slug
            + ") uploaded proof for "
            + (sub.planTier != null ? sub.planTier.name() : "?")
            + " — ref "
            + (sub.mandatoryPaymentReference == null ? "" : sub.mandatoryPaymentReference),
        "SUBSCRIPTION_PROOF_PENDING",
        "SUBSCRIPTION",
        tenant.id.toString());
  }

  private PlatformBankingEntity ensureBanking() {
    return banking.findAll().stream()
        .findFirst()
        .orElseGet(
            () -> {
              PlatformBankingEntity b = new PlatformBankingEntity();
              b.bankName = "";
              b.accountName = "";
              b.accountNumber = "";
              b.branchCode = "";
              b.referenceHint = "Use the mandatory reference from Plan & billing";
              b.paymentLink = "";
              return banking.save(b);
            });
  }

  private static boolean isBankingConfigured(PlatformBankingEntity b) {
    if (b == null) return false;
    return !nz(b.bankName).isBlank()
        && !nz(b.accountName).isBlank()
        && !nz(b.accountNumber).isBlank()
        && !nz(b.accountNumber).equals("0000000000");
  }

  private static Map<String, Object> planToMap(SubscriptionPlanPricingEntity p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("tier", p.tier.name());
    m.put("subscriptionFee", p.subscriptionFee);
    m.put("billingPeriodDays", p.billingPeriodDays);
    m.put("featureInsights", p.featureInsights);
    m.put("featureEmailAlerts", p.featureEmailAlerts);
    m.put("featureWhatsapp", p.featureWhatsapp);
    m.put("featurePayroll", p.featurePayroll);
    m.put("maxEmployees", p.maxEmployees);
    m.put("maxProducts", p.maxProducts);
    return m;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
