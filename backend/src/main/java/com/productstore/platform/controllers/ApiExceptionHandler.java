package com.productstore.platform.controllers;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Set<String> NOT_FOUND =
      Set.of("tenant_not_found", "merchant_not_found", "no_membership", "tenant_missing");
  private static final Set<String> FORBIDDEN = Set.of("forbidden", "not_authenticated");

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex) {
    String code = ex.getMessage() == null ? "bad_request" : ex.getMessage();
    HttpStatus status =
        NOT_FOUND.contains(code)
            ? HttpStatus.NOT_FOUND
            : FORBIDDEN.contains(code) ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
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
}
