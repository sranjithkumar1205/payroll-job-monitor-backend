package com.example.payroll_job_monitor_backend.service;

import com.example.payroll_job_monitor_backend.dto.JobExecutionResponse;
import com.example.payroll_job_monitor_backend.entity.JobExecution;
import com.example.payroll_job_monitor_backend.entity.JobStatus;
import com.example.payroll_job_monitor_backend.repository.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service layer that contains all business logic for payroll job execution.
 *
 * @Slf4j - injects a SLF4J logger field (log) for structured logging.
 * @Service - marks this as a Spring-managed service bean.
 * @RequiredArgsConstructor - Lombok generates a constructor injecting all final
 *                          fields.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionService {

  // Repository used to persist and query job execution records.
  private final JobExecutionRepository jobExecutionRepository;

  /**
   * Creates a new job execution record with RUNNING status and immediately
   * fires off async processing in the background.
   *
   * The method returns right away (non-blocking) so the HTTP response is fast;
   * the actual work happens in a separate thread via processJobAsync().
   *
   * @param jobName name of the payroll job to run
   * @param file    optional file to process during job execution
   * @return the newly created job record (status = RUNNING)
   */
  public JobExecutionResponse triggerJob(String jobName, MultipartFile file) {
    if (file != null && !file.isEmpty()) {
      log.info("File received for job '{}': {}", jobName, file.getOriginalFilename());
    }

    // Build and persist the initial job record before async processing starts.
    JobExecution jobExecution = JobExecution.builder()
        .jobName(jobName)
        .status(JobStatus.RUNNING)
        .startTime(LocalDateTime.now())
        .message("Job started successfully")
        .build();

    JobExecution saved = jobExecutionRepository.save(jobExecution);

    // Kick off background processing; runs on the "jobTaskExecutor" thread pool.
    processJobAsync(saved.getId(), file);

    return toResponse(saved);
  }

  /**
   * Simulates asynchronous job processing on a background thread.
   *
   * @Async tells Spring to run this method in the "jobTaskExecutor" thread pool
   *        (configured in AsyncConfig) instead of the caller's thread.
   *
   *        Flow:
   *        1. Sleep for 3–5 seconds to simulate real processing time.
   *        2. Optionally process the uploaded file.
   *        3. Update the job record to COMPLETED with an end timestamp.
   *        4. On any error, delegate to markJobFailed().
   *
   * @param jobId ID of the job record to update after processing
   * @param file  optional uploaded file to process
   */
  @Async
  public void processJobAsync(Long jobId, MultipartFile file) {
    try {
      int delaySeconds = 3 + new Random().nextInt(3); // 3–5 seconds
      log.info("Processing job id={}, estimated delay={}s", jobId, delaySeconds);
      Thread.sleep(delaySeconds * 1000L);

      if (file != null && !file.isEmpty()) {
        log.info("Simulating file processing for: {}", file.getOriginalFilename());
      }

      // Re-fetch the entity to avoid stale state, then mark it completed.
      jobExecutionRepository.findById(jobId).ifPresent(job -> {
        job.setStatus(JobStatus.COMPLETED);
        job.setEndTime(LocalDateTime.now());
        job.setMessage("Job completed successfully");
        jobExecutionRepository.save(job);
        log.info("Job id={} completed successfully", jobId);
      });
    } catch (InterruptedException e) {
      // Restore the interrupted flag so callers can detect the interruption.
      Thread.currentThread().interrupt();
      markJobFailed(jobId, "Job interrupted: " + e.getMessage());
    } catch (Exception e) {
      log.error("Job id={} failed: {}", jobId, e.getMessage(), e);
      markJobFailed(jobId, "Job failed: " + e.getMessage());
    }
  }

  /**
   * Marks a job as FAILED and records the error message and end time.
   * Called whenever an exception occurs during async processing.
   *
   * @param jobId        ID of the job to mark as failed
   * @param errorMessage description of the failure reason
   */
  private void markJobFailed(Long jobId, String errorMessage) {
    jobExecutionRepository.findById(jobId).ifPresent(job -> {
      job.setStatus(JobStatus.FAILED);
      job.setEndTime(LocalDateTime.now());
      job.setMessage(errorMessage);
      jobExecutionRepository.save(job);
    });
  }

  /**
   * Returns all job execution records from the database, mapped to DTOs.
   *
   * @return list of all job executions (empty list if none exist)
   */
  public List<JobExecutionResponse> getAllJobs() {
    return jobExecutionRepository.findAll()
        .stream()
        .map(this::toResponse) // convert each entity to a response DTO
        .collect(Collectors.toList());
  }

  /**
   * Finds a single job execution by its ID.
   *
   * @param id primary key of the job record
   * @return the matching job as a DTO
   * @throws NoSuchElementException if no job exists with the given ID
   */
  public JobExecutionResponse getJobById(Long id) {
    JobExecution job = jobExecutionRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Job not found with id: " + id));
    return toResponse(job);
  }

  private JobExecutionResponse toResponse(JobExecution job) {
    return JobExecutionResponse.builder()
        .id(job.getId())
        .jobName(job.getJobName())
        .status(job.getStatus())
        .startTime(job.getStartTime())
        .endTime(job.getEndTime())
        .message(job.getMessage())
        .build();
  }
}
