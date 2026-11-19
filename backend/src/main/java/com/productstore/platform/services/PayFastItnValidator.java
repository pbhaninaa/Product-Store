package com.productstore.platform.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import com.productstore.platform.config.EnvFlags;
import com.productstore.platform.config.PayFastProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks beyond the MD5 signature that a PayFast ITN is genuine (PayFast ITN guide, steps 3 and 4):
 * it is addressed to our merchant account, and PayFast itself confirms it via {@code /eng/query/validate}.
 * The signature alone rests on one passphrase; the post-back means a forged ITN fails even if it leaks.
 */
@Component
public class PayFastItnValidator {
  private final PayFastProperties props;
  private final boolean postBack;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public PayFastItnValidator(
      PayFastProperties props, @Value("${payfast.itn-validate:true}") String postBack) {
    this.props = props;
    this.postBack = EnvFlags.parse(postBack, true);
  }

  /** Call only after the signature has been verified, so junk requests never trigger outbound calls. */
  public void validate(Map<String, String> params) {
    if (!props.isEnabled()) {
      throw new IllegalArgumentException("PayFast is disabled");
    }
    String merchantId = params.get("merchant_id");
    if (merchantId == null || !merchantId.trim().equals(props.getMerchantId())) {
      throw new IllegalArgumentException("PayFast notification is for another merchant");
    }
    if (!postBack) {
      return;
    }
    String body =
        params.entrySet().stream()
            .filter(e -> !"signature".equals(e.getKey()))
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(validateUrl()))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      // Not our verdict on the ITN: answer non-200 so PayFast retries later.
      throw new IllegalStateException("payfast_validate_unreachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("payfast_validate_interrupted", e);
    }
    if (response.statusCode() != 200) {
      throw new IllegalStateException("payfast_validate_http_" + response.statusCode());
    }
    if (!"VALID".equals(response.body() == null ? "" : response.body().trim())) {
      throw new IllegalArgumentException("PayFast did not confirm this notification");
    }
  }

  String validateUrl() {
    return props.isSandbox()
        ? "https://sandbox.payfast.co.za/eng/query/validate"
        : "https://www.payfast.co.za/eng/query/validate";
  }

  private static String encode(String v) {
    return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
  }
}
