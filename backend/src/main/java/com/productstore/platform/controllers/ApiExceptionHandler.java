package com.productstore.platform.controllers;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Set<String> NOT_FOUND =
      Set.of(
          "tenant_not_found",
          "merchant_not_found",
          "no_membership",
          "tenant_missing",
          "not_found",
          "order_not_found",
          "booking_not_found");
  private static final Set<String> FORBIDDEN = Set.of("forbidden", "not_authenticated");
  private static final Set<String> GONE =
      Set.of(
          "endpoint_gone",
          "manual_eft_disabled",
          "manual_subscription_activation_disabled",
          "subscription_proof_mutation_disabled",
          "platform_banking_mutation_disabled");

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex) {
    String code = ex.getMessage() == null ? "bad_request" : ex.getMessage();
    HttpStatus status =
        NOT_FOUND.contains(code)
            ? HttpStatus.NOT_FOUND
            : FORBIDDEN.contains(code)
                ? HttpStatus.FORBIDDEN
                : GONE.contains(code) ? HttpStatus.GONE : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(Map.of("error", code));
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> illegalState(IllegalStateException ex) {
    return Map.of("error", ex.getMessage() == null ? "conflict" : ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> validation(MethodArgumentNotValidException ex) {
    return Map.of("error", "validation_error");
  }

  @ExceptionHandler({
    MaxUploadSizeExceededException.class,
    MultipartException.class,
    MissingServletRequestPartException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> multipart(Exception ex) {
    return Map.of("error", "upload_invalid");
  }

  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> io(IOException ex) {
    return Map.of("error", "io_failed");
  }
}
