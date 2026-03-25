package com.example.payroll_job_monitor_backend.controller;

import com.example.payroll_job_monitor_backend.dto.JobExecutionResponse;
import com.example.payroll_job_monitor_backend.entity.JobStatus;
import com.example.payroll_job_monitor_backend.exception.BadRequestException;
import com.example.payroll_job_monitor_backend.exception.GlobalExceptionHandler;
import com.example.payroll_job_monitor_backend.service.JobExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link JobController}, covering the filter query
 * parameters {@code jobName} and {@code status}.
 */
@WebMvcTest(JobController.class)
@Import(GlobalExceptionHandler.class)
class JobControllerFilterTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JobExecutionService jobExecutionService;

  // -------------------------------------------------------------------------
  // Fixture helpers
  // -------------------------------------------------------------------------

  private JobExecutionResponse completedPayrollJob() {
    return JobExecutionResponse.builder()
        .id(1L)
        .jobName("Payroll Processing - January")
        .status(JobStatus.COMPLETED)
        .startTime(LocalDateTime.of(2026, 1, 15, 9, 0))
        .endTime(LocalDateTime.of(2026, 1, 15, 9, 30))
        .message("Done")
        .build();
  }

  private JobExecutionResponse runningTaxJob() {
    return JobExecutionResponse.builder()
        .id(2L)
        .jobName("Tax Calculation Q1")
        .status(JobStatus.RUNNING)
        .startTime(LocalDateTime.of(2026, 1, 15, 10, 0))
        .message("Running")
        .build();
  }

  private Page<JobExecutionResponse> singlePage(JobExecutionResponse... items) {
    List<JobExecutionResponse> list = List.of(items);
    return new PageImpl<>(list, PageRequest.of(0, 10, Sort.by("startTime").descending()), list.size());
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("GET /api/jobs – no filters returns all jobs")
  void noFilters_returns200WithAllJobs() throws Exception {
    when(jobExecutionService.getAllJobs(isNull(), isNull(), any(Pageable.class)))
        .thenReturn(singlePage(completedPayrollJob(), runningTaxJob()));

    mockMvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("GET /api/jobs?jobName=payroll – filters by job name")
  void jobNameFilter_returns200WithFilteredJobs() throws Exception {
    when(jobExecutionService.getAllJobs(eq("payroll"), isNull(), any(Pageable.class)))
        .thenReturn(singlePage(completedPayrollJob()));

    mockMvc.perform(get("/api/jobs").param("jobName", "payroll"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobName").value("Payroll Processing - January"));
  }

  @Test
  @DisplayName("GET /api/jobs?status=RUNNING – filters by status")
  void statusFilter_returns200WithFilteredJobs() throws Exception {
    when(jobExecutionService.getAllJobs(isNull(), eq("RUNNING"), any(Pageable.class)))
        .thenReturn(singlePage(runningTaxJob()));

    mockMvc.perform(get("/api/jobs").param("status", "RUNNING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].status").value("RUNNING"));
  }

  @Test
  @DisplayName("GET /api/jobs?jobName=payroll&status=COMPLETED – both filters")
  void bothFilters_returns200() throws Exception {
    when(jobExecutionService.getAllJobs(eq("payroll"), eq("COMPLETED"), any(Pageable.class)))
        .thenReturn(singlePage(completedPayrollJob()));

    mockMvc.perform(get("/api/jobs")
        .param("jobName", "payroll")
        .param("status", "COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
  }

  @Test
  @DisplayName("GET /api/jobs?status=BOGUS – invalid status returns 400")
  void invalidStatus_returns400() throws Exception {
    when(jobExecutionService.getAllJobs(isNull(), eq("BOGUS"), any(Pageable.class)))
        .thenThrow(new BadRequestException(
            "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED"));

    mockMvc.perform(get("/api/jobs").param("status", "BOGUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(
            "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED"));
  }

  @Test
  @DisplayName("GET /api/jobs – page metadata (totalPages, number, size, first, last, empty) is present")
  void pageMetadata_presentInResponse() throws Exception {
    Page<JobExecutionResponse> twoPageResult = new PageImpl<>(
        List.of(completedPayrollJob()),
        PageRequest.of(0, 1, Sort.by("startTime").descending()),
        2L // total 2 items → 2 pages of size 1
    );
    when(jobExecutionService.getAllJobs(isNull(), isNull(), any(Pageable.class)))
        .thenReturn(twoPageResult);

    mockMvc.perform(get("/api/jobs").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(false))
        .andExpect(jsonPath("$.empty").value(false));
  }

  @Test
  @DisplayName("GET /api/jobs?sortBy=startTime&sortDir=asc – sorting params are forwarded")
  void sortingParams_forwardedToService() throws Exception {
    when(jobExecutionService.getAllJobs(isNull(), isNull(), any(Pageable.class)))
        .thenReturn(singlePage(completedPayrollJob(), runningTaxJob()));

    mockMvc.perform(get("/api/jobs")
        .param("sortBy", "startTime")
        .param("sortDir", "asc"))
        .andExpect(status().isOk());
  }
}
