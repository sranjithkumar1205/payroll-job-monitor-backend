package com.example.payroll_job_monitor_backend.exception;

/**
 * Thrown when a client supplies an invalid request parameter value,
 * such as an unrecognised {@code status} enum string.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
