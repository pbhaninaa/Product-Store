package com.productstore.platform.services;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The Paystack API calls this app makes. Behind an interface so tests can replace it. */
public interface PaystackClient {

  /**
   * {@code POST /refund}: refunds {@code amountCents} (ZAR cents) of the transaction with our reference.
   * {@code merchantNote} is internal and must not contain personal data.
   */
  record RefundRequest(String transactionReference, long amountCents, String currency, String merchantNote) {}

  /**
   * Paystack accepted the refund ({@code status} is Paystack's refund status, e.g. {@code pending}). {@code
   * alreadyRefunded} is true when Paystack refused because the transaction is already fully reversed, which
   * for us means the refund exists.
   */
  record RefundResult(String refundId, String status, boolean alreadyRefunded) {}

  record Bank(String name, String code) {}

  record Transfer(String status, String transferCode, String reference) {}

  /** {@code POST /transaction/initialize}. Amount is in cents (ZAR subunits). */
  record InitializeRequest(
      String email,
      long amountCents,
      String currency,
      String reference,
      String callbackUrl,
      List<String> channels,
      Map<String, Object> metadata) {}

  record InitializeResult(String authorizationUrl, String reference) {}

  /**
   * The fields of a Paystack transaction this app relies on (webhook {@code data} or verify {@code data}).
   * {@code chargedCents} is Paystack's {@code amount} (what was charged, fees passed on to the customer
   * included); {@code requestedAmountCents} is {@code requested_amount} when Paystack sends it (what we asked
   * for), else null. {@code domain} is {@code live} or {@code test}. {@code feesCents} is Paystack's
   * {@code fees} (subunits) when it is a non-negative integer, else null (unknown).
   */
  record Transaction(
      String id,
      String status,
      String reference,
      long chargedCents,
      Long requestedAmountCents,
      String currency,
      String channel,
      String domain,
      Map<String, String> metadata,
      Long feesCents) {

    /** A transaction without a known fee. */
    public Transaction(
        String id,
        String status,
        String reference,
        long chargedCents,
        Long requestedAmountCents,
        String currency,
        String channel,
        String domain,
        Map<String, String> metadata) {
      this(id, status, reference, chargedCents, requestedAmountCents, currency, channel, domain, metadata, null);
    }

    /** A transaction without {@code requested_amount}: what was charged is what was asked for. */
    public Transaction(
        String id,
        String status,
        String reference,
        long amountCents,
        String currency,
        String channel,
        String domain,
        Map<String, String> metadata) {
      this(id, status, reference, amountCents, null, currency, channel, domain, metadata);
    }

    /** The amount to compare with the record: {@code requested_amount} when present, else {@code amount}. */
    public long amountCents() {
      return requestedAmountCents != null ? requestedAmountCents : chargedCents;
    }

    /** When both are present the customer must have been charged at least what was asked for. */
    public boolean chargedCoversRequested() {
      return requestedAmountCents == null || chargedCents >= requestedAmountCents;
    }

    /**
     * The Paystack fee the merchant bears, or null when unknown. When charges were passed to the customer
     * ({@code amount} above {@code requested_amount}) that surcharge is not the merchant's: {@code max(0,
     * fees - max(0, charged - requested))}. Without {@code requested_amount} it is simply {@code fees}.
     */
    public Long feeBorneCents() {
      if (feesCents == null) return null;
      long surcharge = requestedAmountCents == null ? 0 : Math.max(0, chargedCents - requestedAmountCents);
      return Math.max(0, feesCents - surcharge);
    }

    public boolean isSuccess() {
      return "success".equalsIgnoreCase(status);
    }

    /** Paystack's final "not paid" states: the customer left the checkout or the charge failed. */
    public boolean isAbandonedOrFailed() {
      return "abandoned".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status);
    }

    public boolean isLive() {
      return "live".equalsIgnoreCase(domain);
    }

    /**
     * Paystack shows a refunded transaction as {@code reversed} (or a reversal still in progress). Used before
     * retrying a refund so a refund that already exists is never sent twice.
     */
    public boolean isReversed() {
      String s = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
      return s.contains("revers") || s.contains("refund");
    }

    /**
     * Reads a transaction object. Paystack sends {@code metadata} either as an object or as a JSON
     * encoded string, so both are accepted; nested values are ignored.
     */
    public static Transaction fromJson(JsonNode data, ObjectMapper mapper) {
      if (data == null || !data.isObject()) {
        throw new PaystackException("Paystack transaction data missing");
      }
      JsonNode amount = data.get("amount");
      if (amount == null || !amount.canConvertToLong()) {
        throw new PaystackException("Paystack transaction amount missing");
      }
      JsonNode requested = data.get("requested_amount");
      Long requestedCents =
          requested != null && !requested.isNull() && requested.canConvertToLong() ? requested.asLong() : null;
      JsonNode fees = data.get("fees");
      Long feesCents =
          fees != null && fees.isIntegralNumber() && fees.canConvertToLong() && fees.asLong() >= 0
              ? fees.asLong()
              : null;
      return new Transaction(
          text(data, "id"),
          text(data, "status"),
          text(data, "reference"),
          amount.asLong(),
          requestedCents,
          text(data, "currency"),
          text(data, "channel"),
          text(data, "domain"),
          metadata(data.get("metadata"), mapper),
          feesCents);
    }

    private static Map<String, String> metadata(JsonNode raw, ObjectMapper mapper) {
      JsonNode node = raw;
      if (node != null && node.isTextual() && !node.asText().isBlank()) {
        try {
          node = mapper.readTree(node.asText());
        } catch (Exception e) {
          node = null;
        }
      }
      Map<String, String> out = new LinkedHashMap<>();
      if (node == null || !node.isObject()) {
        return out;
      }
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> e = fields.next();
        if (e.getValue() != null && e.getValue().isValueNode() && !e.getValue().isNull()) {
          out.put(e.getKey(), e.getValue().asText());
        }
      }
      return out;
    }

    private static String text(JsonNode node, String field) {
      JsonNode v = node.get(field);
      return v == null || v.isNull() ? null : v.asText();
    }
  }

  /** Paystack could not be reached or answered with an error. The message never contains secrets. */
  class PaystackException extends RuntimeException {
    public PaystackException(String message) {
      super(message);
    }

    public PaystackException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  InitializeResult initialize(InitializeRequest request);

  List<Bank> listSouthAfricanBanks();

  /** Looks up the bank-registered name for a South African account ({@code GET /bank/resolve}). */
  String resolveBankAccount(String accountNumber, String bankCode);

  /** Creates (or returns Paystack's existing) transfer recipient for a South African bank account. */
  String createRecipient(String accountHolder, String accountNumber, String bankCode);

  Transfer transfer(long amountCents, String recipientCode, String reference, String reason);

  /** {@code GET /transaction/verify/{reference}}; empty when Paystack does not know the reference. */
  Optional<Transaction> verify(String reference);

  /**
   * {@code POST /refund}. Throws {@link PaystackException} when Paystack cannot be reached or refuses (other
   * than "already fully reversed", which is returned with {@code alreadyRefunded}).
   */
  RefundResult refund(RefundRequest request);
}
