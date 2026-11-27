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
      String accountType,
      String verifiedName,
      boolean verified) {}

  /**
   * Preview result from Paystack validation. Returns the verified account name that must be confirmed
   * before saving.
   *
   * @deprecated The preview flow is being replaced with full validation. This record is kept for
   *     backward compatibility but will be removed in a future version.
   */
  @Deprecated
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
      return new AccountView(null, null, null, null, null, null, false);
    }
    return new AccountView(
        s.bankCode,
        s.bankName,
        s.bankAccountHolder,
        mask(s.bankAccountNumber),
        s.payoutAccountType,
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
  public AccountView save(
      UUID tenantId,
      String bankCode,
      String accountNumber,
      String confirmedAccountName,
      String accountType,
      String documentType,
      String documentNumber) {
    ShopSettingsEntity s =
        shopSettings.findByTenantId(tenantId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (accountNumber == null || accountNumber.isBlank()) {
      s.bankCode = "";
      s.bankName = s.bankName == null ? "" : s.bankName;
      s.bankAccountHolder = "";
      s.bankAccountNumber = "";
      s.payoutAccountType = null;
      s.payoutAccountVerifiedName = "";
      s.payoutPaystackRecipientCode = null;
      shopSettings.save(s);
      return view(s);
    }
    requirePaystack();
    PaystackClient.Bank bank = requireBank(bankCode);
    String number = requireAccountNumber(accountNumber);
    String type = requireAccountType(accountType);
    String docType = requireDocumentType(documentType);
    String docNumber = requireDocumentNumber(documentNumber);
    String confirmedName = confirmedAccountName == null ? "" : confirmedAccountName.trim();
    if (confirmedName.isEmpty()) {
      throw new IllegalArgumentException("payout_account_name_required");
    }

    // Validate the account with Paystack using the South African validation endpoint
    PaystackClient.ValidationRequest validationRequest =
        new PaystackClient.ValidationRequest(number, bank.code(), confirmedName, type, docType, docNumber);
    PaystackClient.ValidationResult result;
    try {
      result = paystack.validateAccount(validationRequest);
    } catch (PaystackClient.PaystackException e) {
      // Convert Paystack errors to user-friendly messages
      String message = e.getMessage();
      if (message != null && message.toLowerCase().contains("account name")) {
        throw new IllegalArgumentException("payout_account_validation_failed_name");
      } else if (message != null && message.toLowerCase().contains("document")) {
        throw new IllegalArgumentException("payout_account_validation_failed_document");
      } else {
        throw new IllegalArgumentException("payout_account_validation_failed");
      }
    }

    if (!result.verified()) {
      throw new IllegalArgumentException("payout_account_not_verified");
    }

    String verifiedName = result.accountName();
    if (!namesMatch(verifiedName, confirmedName)) {
      throw new IllegalArgumentException("payout_account_name_mismatch");
    }

    boolean changed =
        !bank.code().equals(blank(s.bankCode))
            || !number.equals(digits(s.bankAccountNumber))
            || !type.equals(blank(s.payoutAccountType))
            || !verifiedName.equals(blank(s.payoutAccountVerifiedName));
    s.bankCode = bank.code();
    s.bankName = bank.name();
    s.bankAccountHolder = verifiedName;
    s.bankAccountNumber = number;
    s.payoutAccountType = type;
    s.payoutAccountVerifiedName = verifiedName;
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

  private static String requireAccountType(String accountType) {
    String type = accountType == null ? "" : accountType.trim().toLowerCase();
    if (!type.equals("personal") && !type.equals("business")) {
      throw new IllegalArgumentException("payout_account_type_required");
    }
    return type;
  }

  private static String requireDocumentType(String documentType) {
    String type = documentType == null ? "" : documentType.trim();
    if (!type.equals("identityNumber")
        && !type.equals("passportNumber")
        && !type.equals("businessRegistrationNumber")) {
      throw new IllegalArgumentException("payout_document_type_invalid");
    }
    return type;
  }

  private static String requireDocumentNumber(String documentNumber) {
    String number = documentNumber == null ? "" : documentNumber.trim();
    if (number.isEmpty() || number.length() < 5) {
      throw new IllegalArgumentException("payout_document_number_required");
    }
    // Basic validation: no special characters except hyphens/spaces
    if (!number.matches("[A-Za-z0-9\\s-]+")) {
      throw new IllegalArgumentException("payout_document_number_invalid");
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
