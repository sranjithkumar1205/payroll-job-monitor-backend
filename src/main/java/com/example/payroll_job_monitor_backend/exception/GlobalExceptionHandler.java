package com.example.payroll_job_monitor_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centralised exception handler for all REST controllers.
 * Maps application exceptions to appropriate HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Returns HTTP 400 with a JSON body containing the error message whenever
   * any controller or service throws a {@link BadRequestException}.
   *
   * <p>
   * Example response body:
   * 
   * <pre>
   * {"error": "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED"}
   * </pre>
   */
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", ex.getMessage()));
  }
}
