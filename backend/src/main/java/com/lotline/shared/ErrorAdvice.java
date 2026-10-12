package com.lotline.shared;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ErrorAdvice {
  @ExceptionHandler(DomainError.class)
  ResponseEntity<?> domain(DomainError e) {
    return ResponseEntity.status(e.status)
        .body(
            Map.of(
                "code",
                e.code,
                "message",
                e.getMessage(),
                "details",
                e.details,
                "traceId",
                trace()));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.unprocessableContent()
        .body(
            Map.of(
                "code",
                "INVALID_REQUEST",
                "message",
                "Check the required fields, identifiers and date formats.",
                "traceId",
                trace()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> fields(MethodArgumentNotValidException e) {
    var errors = new java.util.LinkedHashMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.unprocessableContent()
        .body(
            Map.of(
                "code",
                "VALIDATION_FAILED",
                "message",
                "Check these fields: " + String.join(", ", errors.keySet()),
                "details",
                Map.of("fields", errors),
                "traceId",
                trace()));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<?> missingHeader(MissingRequestHeaderException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "IDEMPOTENCY_KEY_REQUIRED",
                "message",
                "Provide an Idempotency-Key for this command.",
                "traceId",
                trace()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "code",
                "RELATION_CONFLICT",
                "message",
                "This code already exists or a referenced record is outside this workspace. Refresh"
                    + " and check the relationship.",
                "traceId",
                trace()));
  }

  @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
  ResponseEntity<?> unavailable(Exception e) {
    return ResponseEntity.status(503)
        .body(
            Map.of(
                "code",
                "BACKEND_WARMING",
                "message",
                "The database is not available yet. Wait briefly and retry with the same command"
                    + " key.",
                "traceId",
                trace()));
  }

  private String trace() {
    return MDC.get("trace_id") == null ? "unavailable" : MDC.get("trace_id");
  }
}
