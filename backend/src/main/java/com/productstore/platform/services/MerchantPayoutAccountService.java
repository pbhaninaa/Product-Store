package com.productstore.platform.services;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.productstore.platform.config.PaystackProperties;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies and stores the bank account Paystack will pay a merchant into. Customers never see these
 * details; the owner must confirm the name Paystack has on the account before we keep it.
 */
@Service
public class MerchantPayoutAccountService {
  public record AccountView(
      String bankCode,
      String bankName,
      String accountHolder,
      String accountNumberMasked,
      String verifiedName,
      boolean verified) {}

  public record ResolvedAccount(String accountName, String bankCode, String bankName) {}

  private final ShopSettingsRepository shopSettings;
  private final PaystackClient paystack;
  private final PaystackProperties properties;

  public MerchantPayoutAccountService(
      ShopSettingsRepository shopSettings, PaystackClient paystack, PaystackProperties properties) {
    this.shopSettings = shopSettings;
    this.paystack = paystack;
    this.properties = properties;
  }

  public List<PaystackClient.Bank> listBanks() {
    return paystack.listSouthAfricanBanks();
  }

  public static boolean isVerified(ShopSettingsEntity s) {
    return s != null
        && notBlank(s.bankCode)
        && digits(s.bankAccountNumber).length() >= 6
        && notBlank(s.payoutAccountVerifiedName);
  }

  public AccountView view(ShopSettingsEntity s) {
    if (s == null) {
      return new AccountView(null, null, null, null, null, false);
    }
    return new AccountView(
        s.bankCode,
        s.bankName,
        s.bankAccountHolder,
        mask(s.bankAccountNumber),
        s.payoutAccountVerifiedName,
        isVerified(s));
  }

  public ResolvedAccount preview(String bankCode, String accountNumber) {
    requirePaystack();
    PaystackClient.Bank bank = requireBank(bankCode);
    String number = requireAccountNumber(accountNumber);
    String name = paystack.resolveBankAccount(number, bank.code());
    return new ResolvedAccount(name, bank.code(), bank.name());
  }

  @Transactional
  public AccountView save(UUID tenantId, String bankCode, String accountNumber, String confirmedAccountName) {
    ShopSettingsEntity s =
        shopSettings.findByTenantId(tenantId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (accountNumber == null || accountNumber.isBlank()) {
      s.bankCode = "";
      s.bankName = s.bankName == null ? "" : s.bankName;
      s.bankAccountHolder = "";
      s.bankAccountNumber = "";
      s.payoutAccountVerifiedName = "";
      s.payoutPaystackRecipientCode = null;
      shopSettings.save(s);
      return view(s);
    }
    requirePaystack();
    PaystackClient.Bank bank = requireBank(bankCode);
    String number = requireAccountNumber(accountNumber);
    String resolvedName = paystack.resolveBankAccount(number, bank.code());
    if (!namesMatch(resolvedName, confirmedAccountName)) {
      throw new IllegalArgumentException("payout_account_name_mismatch");
    }
    boolean changed =
        !bank.code().equals(blank(s.bankCode))
            || !number.equals(digits(s.bankAccountNumber))
            || !resolvedName.equals(blank(s.payoutAccountVerifiedName));
    s.bankCode = bank.code();
    s.bankName = bank.name();
    s.bankAccountHolder = resolvedName;
    s.bankAccountNumber = number;
    s.payoutAccountVerifiedName = resolvedName;
    if (changed) {
      s.payoutPaystackRecipientCode = null;
    }
    shopSettings.save(s);
    return view(s);
  }

  public String ensureRecipient(ShopSettingsEntity s) {
    if (!isVerified(s)) {
      throw new IllegalArgumentException("payout_account_unverified");
    }
    if (notBlank(s.payoutPaystackRecipientCode)) {
      return s.payoutPaystackRecipientCode;
    }
    String code =
        paystack.createRecipient(s.payoutAccountVerifiedName, digits(s.bankAccountNumber), s.bankCode);
    s.payoutPaystackRecipientCode = code;
    shopSettings.save(s);
    return code;
  }

  public static String last4(String accountNumber) {
    String d = digits(accountNumber);
    if (d.length() < 4) {
      return d;
    }
    return d.substring(d.length() - 4);
  }

  public static String mask(String accountNumber) {
    String d = digits(accountNumber);
    if (d.isEmpty()) {
      return "";
    }
    return d.length() <= 4 ? d : "•••• " + d.substring(d.length() - 4);
  }

  static boolean namesMatch(String a, String b) {
    return normalizeName(a).equals(normalizeName(b)) && !normalizeName(a).isEmpty();
  }

  static String normalizeName(String s) {
    if (s == null) {
      return "";
    }
    return s.replaceAll("[^A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
  }

  static String digits(String s) {
    return s == null ? "" : s.replaceAll("\\D", "");
  }

  private void requirePaystack() {
    if (!properties.isConfigured()) {
      throw new IllegalArgumentException("online_payment_unavailable");
    }
  }

  private PaystackClient.Bank requireBank(String bankCode) {
    String code = bankCode == null ? "" : bankCode.trim();
    return listBanks().stream()
        .filter(b -> b.code().equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("payout_bank_required"));
  }

  private static String requireAccountNumber(String accountNumber) {
    String number = digits(accountNumber);
    if (number.length() < 6 || number.length() > 16) {
      throw new IllegalArgumentException("invalid_account_number");
    }
    return number;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String blank(String s) {
    return s == null ? "" : s;
  }
}
