package com.example.payroll_job_monitor_backend.repository;

import com.example.payroll_job_monitor_backend.entity.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link JobExecution} entities.
 *
 * Extending JpaRepository automatically provides standard CRUD operations:
 * - save() : insert or update a record
 * - findById() : look up a record by primary key
 * - findAll() : retrieve all records
 * - deleteById(): remove a record by primary key
 *
 * Custom query methods can be added here if needed (e.g. findByStatus).
 */
@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
}
