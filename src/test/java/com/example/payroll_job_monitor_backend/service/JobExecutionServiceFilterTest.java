package com.example.payroll_job_monitor_backend.service;

import com.example.payroll_job_monitor_backend.dto.JobExecutionResponse;
import com.example.payroll_job_monitor_backend.entity.JobExecution;
import com.example.payroll_job_monitor_backend.entity.JobStatus;
import com.example.payroll_job_monitor_backend.exception.BadRequestException;
import com.example.payroll_job_monitor_backend.repository.JobExecutionRepository;
import com.example.payroll_job_monitor_backend.specification.JobSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the filtering logic inside
 * {@link JobExecutionService#getAllJobs(String, String, Pageable)}.
 *
 * The repository is mocked so these tests run without a real database.
 */
@ExtendWith(MockitoExtension.class)
class JobExecutionServiceFilterTest {

  @Mock
  private JobExecutionRepository jobExecutionRepository;

  @InjectMocks
  private JobExecutionService service;

  private JobExecution payrollJob;
  private JobExecution taxJob;

  @BeforeEach
  void setUp() {
    payrollJob = JobExecution.builder()
        .id(1L)
        .jobName("Payroll Processing - January")
        .status(JobStatus.COMPLETED)
        .startTime(LocalDateTime.now().minusHours(2))
        .endTime(LocalDateTime.now().minusHours(1))
        .message("Done")
        .build();

    taxJob = JobExecution.builder()
        .id(2L)
        .jobName("Tax Calculation Q1")
        .status(JobStatus.RUNNING)
        .startTime(LocalDateTime.now().minusMinutes(10))
        .message("Running")
        .build();
  }

  // -------------------------------------------------------------------------
  // Helper to stub repository.findAll(Specification, Pageable)
  // -------------------------------------------------------------------------

  private void stubRepo(List<JobExecution> jobs, Pageable pageable) {
    Page<JobExecution> page = new PageImpl<>(jobs, pageable, jobs.size());
    when(jobExecutionRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(page);
  }

  private Pageable defaultPageable() {
    return PageRequest.of(0, 10, Sort.by("startTime").descending());
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("No filters: returns all jobs")
  void noFilters_returnsAll() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob, taxJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs(null, null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
    verify(jobExecutionRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  @DisplayName("jobName filter only: delegates specification with non-null trimmed name")
  void jobNameOnly_delegatesSpec() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs("payroll", null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getJobName()).isEqualTo("Payroll Processing - January");
  }

  @Test
  @DisplayName("status filter only: delegates specification with parsed enum")
  void statusOnly_delegatesSpec() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(taxJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs(null, "RUNNING", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getStatus()).isEqualTo(JobStatus.RUNNING);
  }

  @Test
  @DisplayName("Both filters: specification is built with both predicates")
  void bothFilters_combineWithAnd() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs("payroll", "COMPLETED", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  @Test
  @DisplayName("Blank jobName is treated as no filter")
  void blankJobName_ignoredAsFilter() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob, taxJob), pageable);

    // " " is all whitespace – must be ignored
    Page<JobExecutionResponse> result = service.getAllJobs("   ", null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("Empty string jobName is treated as no filter")
  void emptyJobName_ignoredAsFilter() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob, taxJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs("", null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("Status is case-insensitive: 'completed' is accepted")
  void statusCaseInsensitive_accepted() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob), pageable);

    assertThatCode(() -> service.getAllJobs(null, "completed", pageable))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Invalid status throws BadRequestException with allowed values")
  void invalidStatus_throwsBadRequest() {
    Pageable pageable = defaultPageable();

    assertThatThrownBy(() -> service.getAllJobs(null, "INVALID_STATUS", pageable))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid status value")
        .hasMessageContaining("RUNNING")
        .hasMessageContaining("COMPLETED")
        .hasMessageContaining("FAILED");
  }

  @Test
  @DisplayName("Pagination: totalElements and totalPages reflect the filtered dataset")
  void pagination_reflectsFilteredDataset() {
    Pageable pageable = PageRequest.of(0, 1, Sort.by("startTime").descending());
    // Only 1 matching job out of a filtered total of 1
    Page<JobExecution> filteredPage = new PageImpl<>(List.of(payrollJob), pageable, 1L);
    when(jobExecutionRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(filteredPage);

    Page<JobExecutionResponse> result = service.getAllJobs("payroll", "COMPLETED", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(1);
    assertThat(result.getNumber()).isEqualTo(0);
  }

  @Test
  @DisplayName("Sorting: sort by startTime descending is passed through to repository")
  void sorting_passedThroughToRepository() {
    Sort descSort = Sort.by(Sort.Direction.DESC, "startTime");
    Pageable pageable = PageRequest.of(0, 10, descSort);
    stubRepo(List.of(payrollJob, taxJob), pageable);

    service.getAllJobs(null, null, pageable);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(jobExecutionRepository).findAll(any(Specification.class), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("startTime"))
        .isNotNull()
        .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
  }

  @Test
  @DisplayName("Backward-compatible getAllJobs(Pageable) delegates to filter method with nulls")
  void backwardCompatOverload_delegatesWithNullFilters() {
    Pageable pageable = defaultPageable();
    stubRepo(List.of(payrollJob, taxJob), pageable);

    Page<JobExecutionResponse> result = service.getAllJobs(pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
  }
}
