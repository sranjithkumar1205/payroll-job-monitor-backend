package com.example.payroll_job_monitor_backend.controller;

import com.example.payroll_job_monitor_backend.dto.JobExecutionResponse;
import com.example.payroll_job_monitor_backend.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST controller exposing payroll job management endpoints under "/api/jobs".
 *
 * @RestController - marks this class as a controller where every method returns
 *                 a domain object instead of a view (responses are JSON by
 *                 default).
 * @RequestMapping - sets the base URL path for all endpoints in this
 *                 controller.
 * @RequiredArgsConstructor - Lombok injects all final fields via constructor.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

  // Service layer handles all business logic; injected by Spring via constructor.
  private final JobExecutionService jobExecutionService;

  /**
   * POST /api/jobs/trigger
   * Triggers a new payroll job execution.
   *
   * @param jobName name of the job to run (required form field)
   * @param file    optional file to be processed by the job
   * @return 200 OK with the created job record (status will be RUNNING initially)
   */
  @PostMapping(value = "/trigger", consumes = { "multipart/form-data" })
  public ResponseEntity<JobExecutionResponse> triggerJob(
      @RequestParam("jobName") String jobName,
      @RequestParam(value = "file", required = false) MultipartFile file) {

    JobExecutionResponse response = jobExecutionService.triggerJob(jobName, file);
    return ResponseEntity.ok(response);
  }

  /**
   * GET /api/jobs
   * Returns a paginated, optionally filtered list of job executions.
   *
   * <p>
   * Optional query parameters:
   * <ul>
   * <li>{@code jobName} – partial, case-insensitive match against the job
   * name.</li>
   * <li>{@code status} – exact match against the {@code JobStatus} enum
   * (RUNNING, COMPLETED, FAILED). Returns HTTP 400 for invalid values.</li>
   * </ul>
   *
   * <p>
   * Example:
   * {@code GET /api/jobs?page=0&size=10&sortBy=startTime&sortDir=desc&jobName=payroll&status=COMPLETED}
   *
   * @param page    zero-based page index (default 0)
   * @param size    number of records per page (default 10)
   * @param sortBy  field name to sort by (default startTime)
   * @param sortDir sort direction – "asc" or "desc" (default desc)
   * @param jobName optional partial job name filter (case-insensitive)
   * @param status  optional exact status filter (RUNNING, COMPLETED, FAILED)
   * @return 200 OK with a {@link Page} of job executions, or 400 for invalid
   *         status
   */
  @GetMapping
  public ResponseEntity<Page<JobExecutionResponse>> getAllJobs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "startTime") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestParam(required = false) String jobName,
      @RequestParam(required = false) String status) {
    Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    PageRequest pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(jobExecutionService.getAllJobs(jobName, status, pageable));
  }

  /**
   * GET /api/jobs/{id}
   * Retrieves a single job execution by its ID.
   *
   * @param id the primary key of the job execution
   * @return 200 OK with the matching job, or 404 Not Found if no record exists
   */
  @GetMapping("/{id}")
  public ResponseEntity<JobExecutionResponse> getJobById(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(jobExecutionService.getJobById(id));
    } catch (NoSuchElementException e) {
      // Return 404 when the requested job ID does not exist in the database.
      return ResponseEntity.notFound().build();
    }
  }
}
