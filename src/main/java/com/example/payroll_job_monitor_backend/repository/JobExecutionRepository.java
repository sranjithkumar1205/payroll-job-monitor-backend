package com.example.payroll_job_monitor_backend.repository;

import com.example.payroll_job_monitor_backend.entity.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link JobExecution} entities.
 *
 * Extending JpaRepository provides standard CRUD operations.
 * Extending JpaSpecificationExecutor enables dynamic filtering via
 * {@link org.springframework.data.jpa.domain.Specification}.
 */
@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long>,
    JpaSpecificationExecutor<JobExecution> {
}
