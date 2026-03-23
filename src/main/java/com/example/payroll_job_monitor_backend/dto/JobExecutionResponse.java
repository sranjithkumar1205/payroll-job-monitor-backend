package com.example.payroll_job_monitor_backend.dto;

import com.example.payroll_job_monitor_backend.entity.JobStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) returned in all job-related API responses.
 * Decouples the internal JPA entity from the JSON representation sent to
 * clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionResponse {

  /** Unique identifier of the job execution record. */
  private Long id;

  /** Name of the payroll job. */
  private String jobName;

  /** Current status of the job (RUNNING, COMPLETED, FAILED). */
  private JobStatus status;

  /** ISO-8601 formatted start time serialized as "yyyy-MM-dd'T'HH:mm:ss". */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime startTime;

  /** ISO-8601 formatted end time; null if the job is still running. */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime endTime;

  /** Descriptive result or error message populated after job completion. */
  private String message;
}
