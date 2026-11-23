package com.productstore.platform.controllers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.productstore.platform.services.PaystackPaymentService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Paystack endpoints. The webhook is reachable without login; every delivery must carry a valid
 * {@code x-paystack-signature} over the exact request bytes, which is why the body is read as raw bytes.
 * Bodies over {@link #MAX_WEBHOOK_BYTES} are refused before any signature work.
 */
@RestController
@RequestMapping("/api/payments/paystack")
public class PaystackController {
  /** Paystack events are a few kilobytes; anything far larger is not Paystack. */
  static final int MAX_WEBHOOK_BYTES = 64 * 1024;

  private static final Logger log = LoggerFactory.getLogger(PaystackController.class);

  private final PaystackPaymentService paystack;

  public PaystackController(PaystackPaymentService paystack) {
    this.paystack = paystack;
  }

  @GetMapping("/configured")
  public Map<String, Object> configured() {
    return Map.of("configured", paystack.isPlatformConfigured());
  }

  /**
   * 200 when the event was handled or is not ours (Paystack stops retrying), 401 for a bad signature,
   * 413 for an oversized body, 400 when a signed payment does not match its record (Paystack retries; the
   * mismatch is logged).
   */
  @PostMapping("/webhook")
  public ResponseEntity<Void> webhook(
      @RequestHeader(value = "x-paystack-signature", required = false) String signature,
      HttpServletRequest request)
      throws IOException {
    if (request.getContentLengthLong() > MAX_WEBHOOK_BYTES) {
      log.warn("Rejected Paystack webhook: body larger than {} bytes", MAX_WEBHOOK_BYTES);
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }
    byte[] rawBody = readAtMost(request.getInputStream(), MAX_WEBHOOK_BYTES);
    if (rawBody == null) {
      log.warn("Rejected Paystack webhook: body larger than {} bytes", MAX_WEBHOOK_BYTES);
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }
    try {
      paystack.handleWebhook(rawBody, signature);
      return ResponseEntity.ok().build();
    } catch (PaystackPaymentService.InvalidWebhookSignatureException e) {
      log.warn("Rejected Paystack webhook with a missing or invalid signature");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    } catch (IllegalArgumentException e) {
      log.warn("Rejected Paystack webhook: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  /** Reads the body, or returns null as soon as it exceeds {@code max} bytes (covers chunked bodies). */
  static byte[] readAtMost(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      if (out.size() + n > max) {
        return null;
      }
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }
}
