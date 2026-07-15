package com.productstore.platform.services;

import com.productstore.platform.entities.PlatformBankingEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.PlatformBankingRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class SubscriptionPlanBootstrapService implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanBootstrapService.class);

  private final SubscriptionPlanPricingRepository plans;
  private final PlatformBankingRepository banking;
  private final Environment environment;
  private final String bankName;
  private final String accountName;
  private final String accountNumber;
  private final String branchCode;
  private final String referenceHint;
  private final String paymentLink;

  public SubscriptionPlanBootstrapService(
      SubscriptionPlanPricingRepository plans,
      PlatformBankingRepository banking,
      Environment environment,
      @Value("${app.platform-banking.bank-name:}") String bankName,
      @Value("${app.platform-banking.account-name:}") String accountName,
      @Value("${app.platform-banking.account-number:}") String accountNumber,
      @Value("${app.platform-banking.branch-code:}") String branchCode,
      @Value("${app.platform-banking.reference-hint:}") String referenceHint,
      @Value("${app.platform-banking.payment-link:}") String paymentLink) {
    this.plans = plans;
    this.banking = banking;
    this.environment = environment;
    this.bankName = nz(bankName);
    this.accountName = nz(accountName);
    this.accountNumber = nz(accountNumber);
    this.branchCode = nz(branchCode);
    this.referenceHint = nz(referenceHint);
    this.paymentLink = nz(paymentLink);
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedPlanIfAbsent(TenantEntity.SubscriptionPlan.STARTER, 99, false, false, false, false, 1, 25);
    seedPlanIfAbsent(TenantEntity.SubscriptionPlan.STANDARD, 199, true, true, false, true, 5, 100);
    seedPlanIfAbsent(TenantEntity.SubscriptionPlan.PREMIUM, 399, true, true, true, true, -1, -1);
    ensurePlatformBankingRow();
  }

  private void seedPlanIfAbsent(
      TenantEntity.SubscriptionPlan tier,
      double fee,
      boolean insights,
      boolean email,
      boolean whatsapp,
      boolean payroll,
      int maxEmployees,
      int maxProducts) {
    if (plans.findByTier(tier).isPresent()) return;
    SubscriptionPlanPricingEntity p = new SubscriptionPlanPricingEntity();
    p.tier = tier;
    p.subscriptionFee = fee;
    p.billingPeriodDays = 30;
    p.featureInsights = insights;
    p.featureEmailAlerts = email;
    p.featureWhatsapp = whatsapp;
    p.featurePayroll = payroll;
    p.maxEmployees = maxEmployees;
    p.maxProducts = maxProducts;
    plans.save(p);
    log.info("Seeded subscription plan pricing for {}", tier);
  }

  private void ensurePlatformBankingRow() {
    if (banking.count() > 0) return;

    PlatformBankingEntity b = new PlatformBankingEntity();
    boolean localish = isLocalOrSit();
    if (!bankName.isBlank() || !accountNumber.isBlank()) {
      b.bankName = bankName;
      b.accountName = accountName;
      b.accountNumber = accountNumber;
      b.branchCode = branchCode;
      b.referenceHint =
          referenceHint.isBlank()
              ? "Use the mandatory reference shown on Plan & billing"
              : referenceHint;
      b.paymentLink = paymentLink;
      log.info("Seeded platform banking from PLATFORM_BANK_* / app.platform-banking.* env");
    } else if (localish) {
      b.bankName = "FNB";
      b.accountName = "Sphila Group (local)";
      b.accountNumber = "0000000000";
      b.branchCode = "250655";
      b.referenceHint = "Local/SIT placeholder — replace before real EFT tests";
      b.paymentLink = "";
      log.warn("Seeded local/SIT placeholder platform banking (not for production remittances)");
    } else {
      b.bankName = "";
      b.accountName = "";
      b.accountNumber = "";
      b.branchCode = "";
      b.referenceHint = "Set platform banking in Support ? Subscriptions before merchants pay";
      b.paymentLink = "";
      log.warn(
          "Platform banking is empty — set Support ? Subscriptions (or PLATFORM_BANK_* env) before go-live");
    }
    banking.save(b);
  }

  private boolean isLocalOrSit() {
    for (String p : environment.getActiveProfiles()) {
      if ("local".equalsIgnoreCase(p) || "sit".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
        return true;
      }
    }
    return false;
  }

  private static String nz(String s) {
    return s == null ? "" : s.trim();
  }
}
