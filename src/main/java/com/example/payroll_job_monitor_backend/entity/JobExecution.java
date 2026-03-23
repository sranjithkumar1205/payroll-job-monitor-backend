package com.example.payroll_job_monitor_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single payroll job execution record.
 * Maps to the "job_execution" table in the database.
 *
 * Lombok annotations:
 * 
 * @Data - generates getters, setters, equals, hashCode, toString
 * @Builder - enables builder pattern for object construction
 * @NoArgsConstructor / @AllArgsConstructor - required by JPA and builder
 */
@Entity
@Table(name = "job_execution")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {

  /** Auto-incremented primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Human-readable name of the job (e.g. "Payroll Processing - January"). */
  @Column(nullable = false)
  private String jobName;

  /**
   * Current lifecycle status stored as a string in the DB (RUNNING / COMPLETED /
   * FAILED).
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus status;

  /** Timestamp when the job started. */
  private LocalDateTime startTime;

  /** Timestamp when the job finished; null while still RUNNING. */
  private LocalDateTime endTime;

  /** Result or error description set after the job completes or fails. */
  private String message;
}
