package com.productstore.platform.services;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The two Paystack API calls this app makes. Behind an interface so tests can replace it. */
public interface PaystackClient {

  /** {@code POST /transaction/initialize}. Amount is in cents (ZAR subunits). */
  record InitializeRequest(
      String email,
      long amountCents,
      String currency,
      String reference,
      String callbackUrl,
      List<String> channels,
      Map<String, String> metadata) {}

  record InitializeResult(String authorizationUrl, String reference) {}

  /**
   * The fields of a Paystack transaction this app relies on (webhook {@code data} or verify {@code data}).
   * {@code amountCents} is what we asked for ({@code requested_amount} when Paystack sends it, which excludes
   * fees passed on to the customer), else {@code amount}. {@code domain} is {@code live} or {@code test}.
   */
  record Transaction(
      String id,
      String status,
      String reference,
      long amountCents,
      String currency,
      String channel,
      String domain,
      Map<String, String> metadata) {

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
     * Reads a transaction object. Paystack sends {@code metadata} either as an object or as a JSON
     * encoded string, so both are accepted; nested values are ignored.
     */
    public static Transaction fromJson(JsonNode data, ObjectMapper mapper) {
      if (data == null || !data.isObject()) {
        throw new PaystackException("Paystack transaction data missing");
      }
      JsonNode requested = data.get("requested_amount");
      JsonNode amount =
          requested != null && !requested.isNull() && requested.canConvertToLong() ? requested : data.get("amount");
      if (amount == null || !amount.canConvertToLong()) {
        throw new PaystackException("Paystack transaction amount missing");
      }
      return new Transaction(
          text(data, "id"),
          text(data, "status"),
          text(data, "reference"),
          amount.asLong(),
          text(data, "currency"),
          text(data, "channel"),
          text(data, "domain"),
          metadata(data.get("metadata"), mapper));
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

  /** {@code GET /transaction/verify/{reference}}; empty when Paystack does not know the reference. */
  Optional<Transaction> verify(String reference);
}
