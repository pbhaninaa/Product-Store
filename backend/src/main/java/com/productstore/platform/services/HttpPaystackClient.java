package com.productstore.platform.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.config.PaystackProperties;

import org.springframework.stereotype.Component;

/** {@link PaystackClient} over {@code java.net.http} with connect and request timeouts. */
@Component
public class HttpPaystackClient implements PaystackClient {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  /** Verify runs while a customer waits on the return page (and in reconciliation), so it gives up sooner. */
  static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

  private final PaystackProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public HttpPaystackClient(PaystackProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  }

  @Override
  public InitializeResult initialize(InitializeRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", request.email());
    body.put("amount", request.amountCents());
    body.put("currency", request.currency());
    body.put("reference", request.reference());
    body.put("callback_url", request.callbackUrl());
    body.put("channels", request.channels());
    body.put("metadata", request.metadata());
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new PaystackException("Could not build the Paystack request", e);
    }
    HttpRequest httpRequest =
        authorized(URI.create(properties.getBaseUrl() + "/transaction/initialize"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> response = send(httpRequest);
    JsonNode data = successData(response, "initialize");
    String url = text(data, "authorization_url");
    if (url == null || url.isBlank()) {
      throw new PaystackException("Paystack initialize returned no authorization_url");
    }
    return new InitializeResult(url, text(data, "reference"));
  }

  @Override
  public List<Bank> listSouthAfricanBanks() {
    HttpRequest httpRequest =
        authorized(
                URI.create(
                    properties.getBaseUrl()
                        + "/bank?country="
                        + URLEncoder.encode("south africa", StandardCharsets.UTF_8)
                        + "&currency=ZAR&perPage=200"))
            .GET()
            .build();
    JsonNode data = successData(send(httpRequest), "list banks");
    List<Bank> banks = new ArrayList<>();
    if (data != null && data.isArray()) {
      for (JsonNode b : data) {
        if (b.path("active").asBoolean(true) && !b.path("code").asText("").isBlank()) {
          banks.add(new Bank(b.path("name").asText(), b.path("code").asText()));
        }
      }
    }
    return banks;
  }

  @Override
  public String resolveBankAccount(String accountNumber, String bankCode) {
    String number = accountNumber == null ? "" : accountNumber.replaceAll("\\D", "");
    String code = bankCode == null ? "" : bankCode.trim();
    if (number.isEmpty() || code.isEmpty()) {
      throw new PaystackException("Account number and bank are required");
    }
    HttpRequest httpRequest =
        authorized(
                URI.create(
                    properties.getBaseUrl()
                        + "/bank/resolve?account_number="
                        + URLEncoder.encode(number, StandardCharsets.UTF_8)
                        + "&bank_code="
                        + URLEncoder.encode(code, StandardCharsets.UTF_8)))
            .GET()
            .build();
    JsonNode data = successData(send(httpRequest), "resolve");
    String name = data == null ? "" : data.path("account_name").asText("").trim();
    if (name.isBlank()) {
      throw new PaystackException("Paystack did not return the account holder name");
    }
    return name;
  }

  @Override
  public String createRecipient(String accountHolder, String accountNumber, String bankCode) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "basa");
    body.put("name", accountHolder);
    body.put("account_number", accountNumber);
    body.put("bank_code", bankCode);
    body.put("currency", "ZAR");
    JsonNode data = postJson("/transferrecipient", body, "create recipient");
    String code = data == null ? "" : data.path("recipient_code").asText("");
    if (code.isBlank()) {
      throw new PaystackException("Paystack did not return a recipient code");
    }
    return code;
  }

  @Override
  public Transfer transfer(long amountCents, String recipientCode, String reference, String reason) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("source", "balance");
    body.put("amount", amountCents);
    body.put("currency", "ZAR");
    body.put("recipient", recipientCode);
    body.put("reference", reference);
    body.put("reason", reason);
    JsonNode data = postJson("/transfer", body, "transfer");
    return new Transfer(
        data == null ? null : data.path("status").asText(null),
        data == null ? null : data.path("transfer_code").asText(null),
        data == null ? reference : data.path("reference").asText(reference));
  }

  private JsonNode postJson(String path, Map<String, Object> body, String operation) {
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new PaystackException("Could not build the Paystack request", e);
    }
    HttpRequest httpRequest =
        authorized(URI.create(properties.getBaseUrl() + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
    return successData(send(httpRequest), operation);
  }

  @Override
  public Optional<Transaction> verify(String reference) {
    String encoded = URLEncoder.encode(reference == null ? "" : reference, StandardCharsets.UTF_8);
    HttpRequest httpRequest =
        authorized(URI.create(properties.getBaseUrl() + "/transaction/verify/" + encoded), VERIFY_TIMEOUT)
            .GET()
            .build();
    HttpResponse<String> response = send(httpRequest);
    if (isUnknownReference(response)) {
      return Optional.empty();
    }
    return Optional.of(Transaction.fromJson(successData(response, "verify"), mapper));
  }

  @Override
  public RefundResult refund(RefundRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("transaction", request.transactionReference());
    body.put("amount", request.amountCents());
    body.put("currency", request.currency());
    if (request.merchantNote() != null && !request.merchantNote().isBlank()) {
      body.put("merchant_note", request.merchantNote());
    }
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new PaystackException("Could not build the Paystack request", e);
    }
    HttpRequest httpRequest =
        authorized(URI.create(properties.getBaseUrl() + "/refund"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> response = send(httpRequest);
    if (isAlreadyReversed(response)) {
      return new RefundResult(null, "processed", true);
    }
    JsonNode data = successData(response, "refund");
    return new RefundResult(text(data, "id"), text(data, "status"), false);
  }

  /**
   * Paystack refuses a refund for a transaction that is already fully refunded with HTTP 400 and a message
   * such as "Transaction has been fully reversed". For us that means the refund exists: never send another.
   */
  private boolean isAlreadyReversed(HttpResponse<String> response) {
    if (response.statusCode() < 400 || response.statusCode() >= 500) {
      return false;
    }
    try {
      JsonNode root = mapper.readTree(response.body() == null ? "" : response.body());
      String message = root == null ? "" : root.path("message").asText("").toLowerCase(java.util.Locale.ROOT);
      return message.contains("fully reversed") || message.contains("already been reversed")
          || message.contains("already refunded") || message.contains("fully refunded");
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * The OpenAPI spec documents 404 ("Entity not found"); the live API answers an unknown reference with
   * HTTP 400 and {@code "message":"Transaction reference not found"}. Both mean "Paystack has no such
   * transaction"; any other 400 stays an error.
   */
  private boolean isUnknownReference(HttpResponse<String> response) {
    if (response.statusCode() == 404) {
      return true;
    }
    if (response.statusCode() != 400) {
      return false;
    }
    try {
      JsonNode root = mapper.readTree(response.body() == null ? "" : response.body());
      String message = root == null ? "" : root.path("message").asText("");
      return message.toLowerCase(java.util.Locale.ROOT).contains("not found");
    } catch (Exception e) {
      return false;
    }
  }

  private HttpRequest.Builder authorized(URI uri) {
    return authorized(uri, REQUEST_TIMEOUT);
  }

  private HttpRequest.Builder authorized(URI uri, Duration timeout) {
    if (!properties.hasSecretKey() || properties.getBaseUrl().isEmpty()) {
      throw new PaystackException("Paystack is not configured");
    }
    return HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + properties.getSecretKey());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PaystackException("Paystack request interrupted", e);
    } catch (Exception e) {
      throw new PaystackException("Paystack could not be reached", e);
    }
  }

  private JsonNode successData(HttpResponse<String> response, String operation) {
    JsonNode root;
    try {
      root = mapper.readTree(response.body() == null ? "" : response.body());
    } catch (Exception e) {
      throw new PaystackException("Paystack " + operation + " returned an unreadable response (HTTP " + response.statusCode() + ")");
    }
    boolean ok =
        response.statusCode() >= 200
            && response.statusCode() < 300
            && root != null
            && root.path("status").asBoolean(false)
            && root.has("data");
    if (!ok) {
      String message = root == null ? null : text(root, "message");
      throw new PaystackException(
          "Paystack " + operation + " failed (HTTP " + response.statusCode() + ")"
              + (message != null ? ": " + message : ""));
    }
    return root.get("data");
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node == null ? null : node.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }
}
