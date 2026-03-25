package com.example.payroll_job_monitor_backend.specification;

import com.example.payroll_job_monitor_backend.entity.JobExecution;
import com.example.payroll_job_monitor_backend.entity.JobStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable JPA Specification builder for {@link JobExecution} queries.
 *
 * <p>
 * Builds dynamic {@link Predicate} conditions so callers can combine
 * filters without writing raw JPQL or Criteria API code inline.
 * </p>
 */
public final class JobSpecification {

  private JobSpecification() {
    // utility class – no instantiation
  }

  /**
   * Builds a {@link Specification} that applies optional filters:
   * <ul>
   * <li><b>jobName</b> – case-insensitive {@code LIKE %value%} match.</li>
   * <li><b>status</b> – exact enum equality match.</li>
   * </ul>
   * Null or blank values are silently ignored, so callers passing {@code null}
   * for both arguments receive a specification that matches all rows.
   *
   * @param jobName partial job name to search (case-insensitive); may be null
   * @param status  exact status to filter by; may be null
   * @return a composed {@link Specification} (all present conditions joined with
   *         AND)
   */
  public static Specification<JobExecution> withFilters(String jobName, JobStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (jobName != null && !jobName.isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("jobName")),
                "%" + jobName.trim().toLowerCase() + "%"));
      }

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
