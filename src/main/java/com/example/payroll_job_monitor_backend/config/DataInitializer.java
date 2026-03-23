package com.example.payroll_job_monitor_backend.config;

import com.example.payroll_job_monitor_backend.entity.JobExecution;
import com.example.payroll_job_monitor_backend.entity.JobStatus;
import com.example.payroll_job_monitor_backend.repository.JobExecutionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds the in-memory H2 database with sample job execution records on every
 * startup.
 *
 * CommandLineRunner.run() is called by Spring Boot automatically after the
 * application context is fully initialized, making it the ideal hook for
 * loading initial data before any HTTP requests arrive.
 *
 * Note: Because the datasource is configured with "create-drop", all data is
 * lost on shutdown and re-seeded fresh on the next startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

  // Repository used to persist the sample records into the H2 database.
  private final JobExecutionRepository repository;

  public DataInitializer(JobExecutionRepository repository) {
    this.repository = repository;
  }

  /**
   * Inserts four sample job execution records to demonstrate the different
   * job statuses (COMPLETED, FAILED, RUNNING) in the UI.
   *
   * All timestamps are relative to "now" so the data always looks recent.
   *
   * @param args command-line arguments (not used)
   */
  @Override
  public void run(String... args) {
    LocalDateTime now = LocalDateTime.now();

    // Sample 1: Successfully completed payroll job from 3 hours ago.
    repository.save(JobExecution.builder()
        .jobName("Payroll Processing - January")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusHours(3))
        .endTime(now.minusHours(2).minusMinutes(55))
        .message("Processed 1200 employee records successfully.")
        .build());

    // Sample 2: Another successfully completed payroll job from 2 hours ago.
    repository.save(JobExecution.builder()
        .jobName("Payroll Processing - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusHours(2))
        .endTime(now.minusHours(1).minusMinutes(57))
        .message("Processed 1185 employee records successfully.")
        .build());

    // Sample 3: Failed job — demonstrates error handling and FAILED status display.
    repository.save(JobExecution.builder()
        .jobName("Tax Report Generation")
        .status(JobStatus.FAILED)
        .startTime(now.minusMinutes(45))
        .endTime(now.minusMinutes(43))
        .message("Error: Missing tax configuration for department HR-05.")
        .build());

    // Sample 4: Currently running job — endTime is null while still in progress.
    repository.save(JobExecution.builder()
        .jobName("Payroll Processing - March")
        .status(JobStatus.RUNNING)
        .startTime(now.minusMinutes(5))
        .endTime(null) // not finished yet
        .message(null) // message is set only after completion or failure
        .build());
  }
}
