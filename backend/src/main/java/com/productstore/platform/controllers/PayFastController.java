package com.productstore.platform.controllers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.productstore.platform.config.PayFastProperties;
import com.productstore.platform.services.PayFastPaymentService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/payfast")
public class PayFastController {
  private final PayFastPaymentService payFastPaymentService;
  private final PayFastProperties payFastProperties;

  public PayFastController(
      PayFastPaymentService payFastPaymentService, PayFastProperties payFastProperties) {
    this.payFastPaymentService = payFastPaymentService;
    this.payFastProperties = payFastProperties;
  }

  @GetMapping("/configured")
  public Map<String, Object> isConfigured() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("enabled", payFastProperties.isEnabled());
    status.put("configured", payFastPaymentService.isPlatformConfigured());
    status.put("sandbox", payFastProperties.isSandbox());
    status.put("status", payFastProperties.configurationStatus());
    status.put("missingEnvironmentVariables", payFastProperties.missingConfiguration());
    return status;
  }

  /** PayFast ITN is publicly reachable; every payload must carry a valid MD5 signature. */
  @PostMapping("/webhook")
  public ResponseEntity<String> webhook(
      HttpServletRequest request, @RequestBody(required = false) String rawBody) {
    try {
      payFastPaymentService.handleWebhook(extractParams(request, rawBody));
      return ResponseEntity.ok("OK");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("INVALID");
    }
  }

  @PostMapping("/subscription/webhook")
  public ResponseEntity<String> subscriptionWebhook(
      HttpServletRequest request, @RequestBody(required = false) String rawBody) {
    try {
      payFastPaymentService.handleSubscriptionWebhook(extractParams(request, rawBody));
      return ResponseEntity.ok("OK");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("INVALID");
    }
  }

  static Map<String, String> extractParams(HttpServletRequest request, String rawBody) {
    Map<String, String> params = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (values != null && values.length > 0 && values[0] != null) {
                params.put(key, values[0]);
              }
            });
    String contentType = request.getContentType();
    if (params.isEmpty()
        && rawBody != null
        && !rawBody.isBlank()
        && contentType != null
        && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
      for (String pair : rawBody.split("&")) {
        int separator = pair.indexOf('=');
        String key = separator >= 0 ? pair.substring(0, separator) : pair;
        String value = separator >= 0 ? pair.substring(separator + 1) : "";
        params.putIfAbsent(
            URLDecoder.decode(key, StandardCharsets.UTF_8),
            URLDecoder.decode(value, StandardCharsets.UTF_8));
      }
    }
    return params;
  }
}
