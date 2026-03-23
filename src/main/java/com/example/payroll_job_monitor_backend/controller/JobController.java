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
   * Retrieves all job execution records from the database.
   *
   * @return 200 OK with a list of all job executions (may be empty)
   */
  @GetMapping
  public ResponseEntity<Page<JobExecutionResponse>> getAllJobs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "startTime") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir) {
    Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    PageRequest pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(jobExecutionService.getAllJobs(pageable));
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
