package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.config.PaystackProperties;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The real HTTP client against a local stub of the two Paystack endpoints. */
class HttpPaystackClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<String> lastMethod = new AtomicReference<>();
  private final AtomicReference<String> lastPath = new AtomicReference<>();
  private final AtomicReference<String> lastAuth = new AtomicReference<>();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
  private final AtomicReference<String> nextBody = new AtomicReference<>("{}");
  private HttpServer server;
  private HttpPaystackClient client;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastMethod.set(exchange.getRequestMethod());
          lastPath.set(exchange.getRequestURI().getRawPath());
          lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] out = nextBody.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(nextStatus.get(), out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
    PaystackProperties properties = new PaystackProperties();
    ReflectionTestUtils.setField(properties, "enabled", "true");
    ReflectionTestUtils.setField(properties, "secretKey", "sk_test_http");
    ReflectionTestUtils.setField(properties, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    client = new HttpPaystackClient(properties, mapper);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void initializePostsJsonWithBearerSecret() throws Exception {
    nextBody.set(
        "{\"status\":true,\"message\":\"Authorization URL created\",\"data\":{"
            + "\"authorization_url\":\"https://checkout.paystack.com/0peioxfhpn\",\"access_code\":\"0peioxfhpn\","
            + "\"reference\":\"ORDABC\"}}");

    PaystackClient.InitializeResult result =
        client.initialize(
            new PaystackClient.InitializeRequest(
                "a@b.co", 15050, "ZAR", "ORDABC", "https://shop.test/return", List.of("card"),
                Map.<String, Object>of("kind", "order", "id", "42")));

    assertThat(result.authorizationUrl()).isEqualTo("https://checkout.paystack.com/0peioxfhpn");
    assertThat(lastMethod.get()).isEqualTo("POST");
    assertThat(lastPath.get()).isEqualTo("/transaction/initialize");
    assertThat(lastAuth.get()).isEqualTo("Bearer sk_test_http");
    JsonNode sent = mapper.readTree(lastBody.get());
    assertThat(sent.get("email").asText()).isEqualTo("a@b.co");
    assertThat(sent.get("amount").isIntegralNumber()).isTrue();
    assertThat(sent.get("amount").asLong()).isEqualTo(15050);
    assertThat(sent.get("currency").asText()).isEqualTo("ZAR");
    assertThat(sent.get("reference").asText()).isEqualTo("ORDABC");
    assertThat(sent.get("callback_url").asText()).isEqualTo("https://shop.test/return");
    assertThat(sent.get("channels").get(0).asText()).isEqualTo("card");
    assertThat(sent.get("metadata").get("kind").asText()).isEqualTo("order");
  }

  @Test
  void initializeErrorDoesNotLeakTheSecret() {
    nextStatus.set(400);
    nextBody.set("{\"status\":false,\"message\":\"Duplicate Transaction Reference\"}");

    assertThatThrownBy(
            () ->
                client.initialize(
                    new PaystackClient.InitializeRequest(
                        "a@b.co", 100, "ZAR", "R", "https://x", List.of("card"), Map.<String, Object>of())))
        .isInstanceOf(PaystackClient.PaystackException.class)
        .hasMessageContaining("Duplicate Transaction Reference")
        .hasMessageNotContaining("sk_test_http");
  }

  @Test
  void verifyReadsTheTransaction() {
    nextBody.set(
        "{\"status\":true,\"message\":\"Verification successful\",\"data\":{\"id\":4099260516,"
            + "\"status\":\"success\",\"reference\":\"ORDABC\",\"amount\":15050,\"currency\":\"ZAR\","
            + "\"channel\":\"card\",\"metadata\":{\"kind\":\"order\",\"id\":\"42\"}}}");

    Optional<PaystackClient.Transaction> t = client.verify("ORDABC");

    assertThat(lastMethod.get()).isEqualTo("GET");
    assertThat(lastPath.get()).isEqualTo("/transaction/verify/ORDABC");
    assertThat(t).isPresent();
    assertThat(t.get().isSuccess()).isTrue();
    assertThat(t.get().id()).isEqualTo("4099260516");
    assertThat(t.get().amountCents()).isEqualTo(15050);
    assertThat(t.get().metadata()).containsEntry("kind", "order").containsEntry("id", "42");
  }

  /** What the live API answers for a reference it does not know. */
  @Test
  void verifyOfUnknownReferenceIsEmpty() {
    nextStatus.set(400);
    nextBody.set("{\"status\":false,\"message\":\"Transaction reference not found\",\"meta\":{\"nextStep\":\"Ensure that you're passing the reference of a transaction that exists on this integration\"},\"type\":\"validation_error\",\"code\":\"transaction_not_found\"}");

    assertThat(client.verify("NOPE")).isEmpty();
  }

  /** What the OpenAPI spec documents for the same case. */
  @Test
  void verifyAnswering404IsEmpty() {
    nextStatus.set(404);
    nextBody.set("{\"status\":false,\"message\":\"Entity not found\"}");

    assertThat(client.verify("NOPE")).isEmpty();
  }

  @Test
  void otherBadRequestsStayErrors() {
    nextStatus.set(400);
    nextBody.set("{\"status\":false,\"message\":\"Invalid key\"}");

    assertThatThrownBy(() -> client.verify("ORDX")).isInstanceOf(PaystackClient.PaystackException.class);
  }

  /** Paystack's {@code fees} is in subunits; anything but a non-negative integer is unknown (null). */
  @Test
  void verifyReadsTheFeeAndTreatsAMissingOrInvalidFeeAsUnknown() throws Exception {
    nextBody.set(
        "{\"status\":true,\"data\":{\"id\":1,\"domain\":\"test\",\"status\":\"success\",\"reference\":\"ORDF\","
            + "\"amount\":100000,\"fees\":2500,\"fees_split\":null,\"currency\":\"ZAR\",\"channel\":\"card\","
            + "\"metadata\":{}}}");
    PaystackClient.Transaction t = client.verify("ORDF").orElseThrow();
    assertThat(t.feesCents()).isEqualTo(2500L);
    assertThat(t.feeBorneCents()).isEqualTo(2500L);

    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    for (String fees : List.of("", ",\"fees\":null", ",\"fees\":-5", ",\"fees\":12.5", ",\"fees\":\"250\"")) {
      PaystackClient.Transaction parsed =
          PaystackClient.Transaction.fromJson(
              mapper.readTree("{\"amount\":100,\"status\":\"success\"" + fees + "}"), mapper);
      assertThat(parsed.feesCents()).as(fees).isNull();
      assertThat(parsed.feeBorneCents()).as(fees).isNull();
    }
  }

  @Test
  void verifyReadsDomainAndPrefersRequestedAmount() {
    nextBody.set(
        "{\"status\":true,\"data\":{\"id\":1,\"domain\":\"live\",\"status\":\"success\",\"reference\":\"ORDX\","
            + "\"amount\":15500,\"requested_amount\":15050,\"currency\":\"ZAR\",\"channel\":\"card\",\"metadata\":\"\"}}");

    PaystackClient.Transaction t = client.verify("ORDX").orElseThrow();

    assertThat(t.isLive()).isTrue();
    assertThat(t.amountCents()).isEqualTo(15050);
    assertThat(t.metadata()).isEmpty();
  }
  // ------------------------------------------------------------------ refunds (booking fees)

  @Test
  void refundPostsTheReferenceAmountAndCurrency() throws Exception {
    nextBody.set(
        "{\"status\":true,\"message\":\"Refund has been queued for processing\",\"data\":{"
            + "\"transaction\":{\"id\":1004723697,\"reference\":\"BKGABC\"},\"status\":\"pending\",\"id\":3018284,"
            + "\"amount\":20000,\"currency\":\"ZAR\"}}");

    PaystackClient.RefundResult r =
        client.refund(new PaystackClient.RefundRequest("BKGABC", 20000, "ZAR", "Booking fee refund (no_show)"));

    assertThat(lastMethod.get()).isEqualTo("POST");
    assertThat(lastPath.get()).isEqualTo("/refund");
    assertThat(lastAuth.get()).isEqualTo("Bearer sk_test_http");
    JsonNode sent = mapper.readTree(lastBody.get());
    assertThat(sent.get("transaction").asText()).isEqualTo("BKGABC");
    assertThat(sent.get("amount").asLong()).isEqualTo(20000);
    assertThat(sent.get("currency").asText()).isEqualTo("ZAR");
    assertThat(sent.get("merchant_note").asText()).isEqualTo("Booking fee refund (no_show)");
    assertThat(r.refundId()).isEqualTo("3018284");
    assertThat(r.status()).isEqualTo("pending");
    assertThat(r.alreadyRefunded()).isFalse();
  }

  /** A transaction that is already fully refunded means the refund exists: never an error, never sent again. */
  @Test
  void refundOfAFullyReversedTransactionIsReportedAsAlreadyRefunded() {
    nextStatus.set(400);
    nextBody.set("{\"status\":false,\"message\":\"Transaction has been fully reversed\"}");

    PaystackClient.RefundResult r = client.refund(new PaystackClient.RefundRequest("BKGABC", 20000, "ZAR", null));

    assertThat(r.alreadyRefunded()).isTrue();
  }

  @Test
  void refundServerErrorOrMalformedAnswerIsAPaystackErrorWithoutTheSecret() {
    nextStatus.set(500);
    nextBody.set("{\"status\":false,\"message\":\"Internal error\"}");
    assertThatThrownBy(() -> client.refund(new PaystackClient.RefundRequest("BKGABC", 20000, "ZAR", null)))
        .isInstanceOf(PaystackClient.PaystackException.class)
        .hasMessageContaining("HTTP 500")
        .hasMessageNotContaining("sk_test_http");

    nextStatus.set(200);
    nextBody.set("not json");
    assertThatThrownBy(() -> client.refund(new PaystackClient.RefundRequest("BKGABC", 20000, "ZAR", null)))
        .isInstanceOf(PaystackClient.PaystackException.class);

    nextStatus.set(400);
    nextBody.set("{\"status\":false,\"message\":\"Refund amount cannot be greater than transaction amount\"}");
    assertThatThrownBy(() -> client.refund(new PaystackClient.RefundRequest("BKGABC", 20000, "ZAR", null)))
        .isInstanceOf(PaystackClient.PaystackException.class)
        .hasMessageContaining("cannot be greater");
  }

  @Test
  void reversedTransactionsAreRecognised() {
    PaystackClient.Transaction reversed =
        new PaystackClient.Transaction("1", "reversed", "BKGABC", 20000, "ZAR", "card", "test", Map.of());
    PaystackClient.Transaction success =
        new PaystackClient.Transaction("1", "success", "BKGABC", 20000, "ZAR", "card", "test", Map.of());
    assertThat(reversed.isReversed()).isTrue();
    assertThat(success.isReversed()).isFalse();
  }
}
