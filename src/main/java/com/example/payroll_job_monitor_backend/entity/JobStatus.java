package com.example.payroll_job_monitor_backend.entity;

/**
 * Represents the lifecycle states of a payroll job execution.
 */
public enum JobStatus {
  /** Job is currently being processed. */
  RUNNING,

  /** Job finished without errors. */
  COMPLETED,

  /** Job encountered an error and did not finish successfully. */
  FAILED
}
