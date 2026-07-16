package com.productstore.platform.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MerchantSubscriptionService {
  private static final Logger log = LoggerFactory.getLogger(MerchantSubscriptionService.class);
  private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
  private static final DateTimeFormatter REF_TS = DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZONE);

  private final MerchantSubscriptionRepository subscriptions;
  private final SubscriptionPlanPricingRepository plans;
  private final PlatformBankingRepository banking;
  private final TenantRepository tenants;
  private final EmployeeRepository employees;
  private final ProductRepository products;
  private final EftProofDocumentAnalyzer eftProofAnalyzer;
  private final InAppNotificationService inAppNotifications;
  private final String uploadsDir;

  public MerchantSubscriptionService(
      MerchantSubscriptionRepository subscriptions,
      SubscriptionPlanPricingRepository plans,
      PlatformBankingRepository banking,
      TenantRepository tenants,
      EmployeeRepository employees,
      ProductRepository products,
      EftProofDocumentAnalyzer eftProofAnalyzer,
      InAppNotificationService inAppNotifications,
      @Value("${app.uploads.dir:./data/uploads}") String uploadsDir) {
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.banking = banking;
    this.tenants = tenants;
    this.employees = employees;
    this.products = products;
    this.eftProofAnalyzer = eftProofAnalyzer;
    this.inAppNotifications = inAppNotifications;
    this.uploadsDir = uploadsDir;
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
      row.put("periodEnd", sub.periodEnd != null ? sub.periodEnd.toString() : null);
      row.put(
          "paymentProofStatus",
          sub.paymentProofStatus != null ? sub.paymentProofStatus.name() : "NONE");
      out.add(row);
    }
    return out;
  }

  public Map<String, Object> buildStatus(UUID tenantId) {
    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    SubscriptionPlanPricingEntity billingPreview =
        sub.planTier != null
            ? plans.findByTier(sub.planTier).orElse(null)
            : plans.findByTier(TenantEntity.SubscriptionPlan.STARTER).orElse(null);
    TenantEntity.SubscriptionPlan entitlementTier = effectiveEntitlementTier(sub);
    SubscriptionPlanPricingEntity entitlementPlan =
        entitlementTier != null ? plans.findByTier(entitlementTier).orElse(null) : null;

    double fee = billingPreview != null ? billingPreview.subscriptionFee : 0;
    boolean valid = isSubscriptionValid(sub);

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
    boolean needsForInactive = sub.planTier != null && !valid && proofClear;
    boolean needsForUpgrade =
        sub.planTier != null
            && valid
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
    m.put("active", sub.active);
    m.put("valid", valid);
    m.put("periodStart", sub.periodStart != null ? sub.periodStart.toString() : null);
    m.put("periodEnd", sub.periodEnd != null ? sub.periodEnd.toString() : null);
    m.put("subscriptionFee", fee);
    m.put("grandTotalDue", round2(fee));
    m.put("amountDueThisPeriod", round2(fee));
    m.put("billingPeriodDays", billingPreview != null ? billingPreview.billingPeriodDays : 30);
    m.put("maxEmployees", entitlementPlan != null ? entitlementPlan.maxEmployees : null);
    m.put("maxProducts", entitlementPlan != null ? entitlementPlan.maxProducts : null);
    m.put("features", features);
    m.put("needsPlanSelection", sub.planTier == null);
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
    m.put("paymentProofPendingReview", ps == SubscriptionPaymentProofStatus.PENDING);
    m.put("platformBankingConfigured", isBankingConfigured(ensureBanking()));
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
    TenantEntity.SubscriptionPlan previous = sub.planTier;
    sub.planTier = tier;
    tenant.subscriptionPlan = tier;
    tenants.save(tenant);
    if (previous != null && previous != tier) {
      clearPaymentProof(sub);
    }
    if (previous == null || previous != tier) {
      regenerateMandatoryPaymentReference(sub, tenant);
      inAppNotifications.notifyTenantStaff(
          tenantId,
          "Complete your subscription payment",
          "Your plan was updated. Open Plan & billing to pay and upload your proof.",
          "SUBSCRIPTION_ACTION_REQUIRED",
          "SUBSCRIPTION",
          tenantId.toString());
    }
    subscriptions.save(sub);
    return buildStatus(tenantId);
  }

  @Transactional
  public Map<String, Object> uploadPaymentProof(UUID tenantId, MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("proof_required");
    }
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.planTier == null) {
      throw new IllegalStateException("select_plan_first");
    }
    if (!isBankingConfigured(ensureBanking())) {
      throw new IllegalStateException("platform_banking_not_configured");
    }
    boolean valid = isSubscriptionValid(sub);
    boolean upgradingWhileActive =
        valid
            && sub.billedPlanTier != null
            && sub.planTier != null
            && !sub.planTier.equals(sub.billedPlanTier);
    if (valid && !upgradingWhileActive) {
      throw new IllegalStateException("already_active_for_current_plan");
    }
    SubscriptionPaymentProofStatus cur =
        sub.paymentProofStatus != null ? sub.paymentProofStatus : SubscriptionPaymentProofStatus.NONE;
    if (cur == SubscriptionPaymentProofStatus.PENDING
        || cur == SubscriptionPaymentProofStatus.REJECTED
        || (upgradingWhileActive && cur == SubscriptionPaymentProofStatus.APPROVED)) {
      deleteProofFile(sub.paymentProofRelativePath);
    }
    if (!upgradingWhileActive && cur == SubscriptionPaymentProofStatus.APPROVED) {
      throw new IllegalStateException("payment_already_verified");
    }

    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    if (sub.mandatoryPaymentReference == null || sub.mandatoryPaymentReference.isBlank()) {
      regenerateMandatoryPaymentReference(sub, tenant);
    }

    byte[] payload = file.getBytes();
    String ext = eftProofAnalyzer.resolveProofUploadExtension(file.getOriginalFilename(), payload);
    if (!"pdf".equals(ext)) {
      throw new IllegalArgumentException("pdf_required");
    }

    SubscriptionPlanPricingEntity pricing =
        plans.findByTier(sub.planTier).orElseThrow(() -> new IllegalStateException("plan_missing"));
    double expected = pricing.subscriptionFee;

    String rel;
    try {
      rel = storePdf(tenantId, payload);
    } catch (IOException e) {
      log.error("Failed to store subscription proof for tenant {}: {}", tenantId, e.toString());
      throw new IllegalStateException("proof_storage_failed");
    }

    boolean autoOk = false;
    try {
      autoOk =
          eftProofAnalyzer.verifyPdfAmountDateAndReference(
              payload,
              BigDecimal.valueOf(expected).setScale(2, RoundingMode.HALF_UP),
              ZONE,
              sub.mandatoryPaymentReference);
    } catch (Exception e) {
      log.warn("Subscription proof auto-verify crashed tenant={}: {}", tenantId, e.toString());
    }
    String summary =
        autoOk
            ? "Auto-verified: amount, date, and payment reference matched."
            : "Could not auto-verify amount/date/reference; queued for support review.";

    sub.paymentProofRelativePath = rel;
    sub.paymentProofOriginalFilename = file.getOriginalFilename();
    sub.paymentProofUploadedAt = Instant.now();
    sub.paymentProofReviewedAt = null;
    sub.paymentProofRejectionNote = null;
    sub.paymentProofExpectedFee = expected;
    sub.paymentProofAutoPassed = autoOk;
    sub.paymentProofAutoSummary = summary;

    if (autoOk) {
      sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
      sub.paymentProofReviewedAt = Instant.now();
      subscriptions.save(sub);
      activatePeriod(tenantId);
      return buildStatus(tenantId);
    }

    sub.paymentProofStatus = SubscriptionPaymentProofStatus.PENDING;
    subscriptions.save(sub);
    try {
      logPendingProof(tenant, sub);
    } catch (Exception e) {
      log.warn(
          "Failed to notify platform staff of pending proof tenant={}: {}", tenantId, e.toString());
    }
    return buildStatus(tenantId);
  }

  /**
   * Activates a plan without EFT proof (demo bootstrap, support override, tests).
   * Same period rules as a successful approved proof.
   */
  @Transactional
  public Map<String, Object> forceActivatePlan(UUID tenantId, TenantEntity.SubscriptionPlan tier) {
    if (tier == null) throw new IllegalArgumentException("tier_required");
    plans.findByTier(tier).orElseThrow(() -> new IllegalArgumentException("invalid_plan_tier"));
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    sub.planTier = tier;
    clearPaymentProof(sub);
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
    sub.paymentProofReviewedAt = Instant.now();
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
    subscriptions.save(sub);

    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    tenant.subscriptionPlan = sub.billedPlanTier;
    tenants.save(tenant);

    inAppNotifications.notifyTenantStaff(
        tenantId,
        "Subscription activated",
        "Your " + sub.billedPlanTier.name() + " plan is active until " + end + ".",
        "SUBSCRIPTION_ACTIVATED",
        "SUBSCRIPTION",
        tenantId.toString());
    return buildStatus(tenantId);
  }

  @Transactional
  public Map<String, Object> approveProof(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.paymentProofStatus != SubscriptionPaymentProofStatus.PENDING) {
      throw new IllegalStateException("not_pending");
    }
    Path proofPath = resolveProofFile(tenantId);
    byte[] pdfBytes;
    try {
      pdfBytes = Files.readAllBytes(proofPath);
    } catch (IOException e) {
      throw new IllegalStateException("proof_unreadable");
    }
    BigDecimal expected =
        BigDecimal.valueOf(sub.paymentProofExpectedFee).setScale(2, RoundingMode.HALF_UP);
    String ref = sub.mandatoryPaymentReference == null ? "" : sub.mandatoryPaymentReference;
    if (!eftProofAnalyzer.verifyPdfAmountAndReference(pdfBytes, expected, ref)) {
      throw new IllegalStateException("eft_proof_amount_or_reference_mismatch");
    }
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.APPROVED;
    sub.paymentProofReviewedAt = Instant.now();
    subscriptions.save(sub);
    return activatePeriod(tenantId);
  }

  @Transactional
  public Map<String, Object> rejectProof(UUID tenantId, String note) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.paymentProofStatus != SubscriptionPaymentProofStatus.PENDING) {
      throw new IllegalStateException("not_pending");
    }
    sub.paymentProofStatus = SubscriptionPaymentProofStatus.REJECTED;
    sub.paymentProofReviewedAt = Instant.now();
    sub.paymentProofRejectionNote = note == null ? "" : note.trim();
    subscriptions.save(sub);
    inAppNotifications.notifyTenantStaff(
        tenantId,
        "Subscription proof rejected",
        sub.paymentProofRejectionNote.isBlank()
            ? "Please re-upload a clearer bank PDF with the correct reference and amount."
            : sub.paymentProofRejectionNote,
        "SUBSCRIPTION_PROOF_REJECTED",
        "SUBSCRIPTION",
        tenantId.toString());
    return buildStatus(tenantId);
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
    PlatformBankingEntity b = ensureBanking();
    if (body.get("bankName") != null) b.bankName = String.valueOf(body.get("bankName")).trim();
    if (body.get("accountName") != null) b.accountName = String.valueOf(body.get("accountName")).trim();
    if (body.get("accountNumber") != null)
      b.accountNumber = String.valueOf(body.get("accountNumber")).trim();
    if (body.get("branchCode") != null) b.branchCode = String.valueOf(body.get("branchCode")).trim();
    if (body.get("referenceHint") != null)
      b.referenceHint = String.valueOf(body.get("referenceHint")).trim();
    if (body.get("paymentLink") != null) b.paymentLink = String.valueOf(body.get("paymentLink")).trim();
    banking.save(b);
    return getPlatformBanking();
  }

  public boolean hasEffectiveSubscription(UUID tenantId) {
    return isSubscriptionValid(ensureSubscriptionRow(tenantId));
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

  /** Creates the inactive subscription row + choose STARTER guidance for new merchants. */
  @Transactional
  public void provisionNewMerchantSubscription(UUID tenantId) {
    MerchantSubscriptionEntity sub = ensureSubscriptionRow(tenantId);
    if (sub.planTier == null) {
      sub.planTier = TenantEntity.SubscriptionPlan.STARTER;
    }
    TenantEntity tenant =
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    if (tenant.subscriptionPlan == null) {
      tenant.subscriptionPlan = TenantEntity.SubscriptionPlan.STARTER;
      tenants.save(tenant);
    }
    regenerateMandatoryPaymentReference(sub, tenant);
    subscriptions.save(sub);
    inAppNotifications.notifyTenantStaff(
        tenantId,
        "Activate your subscription",
        "Open Plan & billing to choose a plan and pay the period fee so Team, Insights, and alerts unlock.",
        "SUBSCRIPTION_ACTION_REQUIRED",
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
    return subscriptions
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
  }

  private boolean isSubscriptionValid(MerchantSubscriptionEntity sub) {
    if (sub == null || !sub.active || sub.periodStart == null || sub.periodEnd == null) return false;
    LocalDate today = LocalDate.now(ZONE);
    return !today.isBefore(sub.periodStart) && !today.isAfter(sub.periodEnd);
  }

  private TenantEntity.SubscriptionPlan effectiveEntitlementTier(MerchantSubscriptionEntity sub) {
    if (!isSubscriptionValid(sub)) return null;
    return sub.billedPlanTier != null ? sub.billedPlanTier : sub.planTier;
  }

  private void regenerateMandatoryPaymentReference(MerchantSubscriptionEntity sub, TenantEntity tenant) {
    Instant now = Instant.now();
    String slug =
        tenant.slug == null
            ? "SHOP"
            : tenant.slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
    if (slug.length() > 8) slug = slug.substring(0, 8);
    if (slug.isEmpty()) slug = "SHOP";
    String id4 = tenant.id.toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    String ref = "PS-" + slug + "-" + REF_TS.format(now) + "-" + id4;
    if (ref.length() > 64) ref = ref.substring(0, 64);
    sub.mandatoryPaymentReference = ref;
    sub.paymentReferenceGeneratedAt = now;
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
