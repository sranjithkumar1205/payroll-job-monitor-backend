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

    // Sample 5: Completed bonus disbursement job from yesterday.
    repository.save(JobExecution.builder()
        .jobName("Bonus Disbursement - Q4 2025")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(1).minusHours(4))
        .endTime(now.minusDays(1).minusHours(3).minusMinutes(50))
        .message("Processed 980 bonus records successfully.")
        .build());

    // Sample 6: Failed overtime calculation job.
    repository.save(JobExecution.builder()
        .jobName("Overtime Calculation - February")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(1).minusHours(2))
        .endTime(now.minusDays(1).minusHours(1).minusMinutes(58))
        .message("Error: Invalid shift data for department OPS-12.")
        .build());

    // Sample 7: Completed employee deduction sync.
    repository.save(JobExecution.builder()
        .jobName("Employee Deduction Sync - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(2).minusHours(6))
        .endTime(now.minusDays(2).minusHours(5).minusMinutes(52))
        .message("Synced deductions for 1150 employees.")
        .build());

    // Sample 8: Completed provident fund contribution job.
    repository.save(JobExecution.builder()
        .jobName("PF Contribution Processing - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(2).minusHours(3))
        .endTime(now.minusDays(2).minusHours(2).minusMinutes(55))
        .message("PF contributions processed for 1150 employees.")
        .build());

    // Sample 9: Failed compliance report due to missing data.
    repository.save(JobExecution.builder()
        .jobName("Compliance Report - January")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(3).minusHours(5))
        .endTime(now.minusDays(3).minusHours(4).minusMinutes(58))
        .message("Error: Missing statutory data for 12 employees.")
        .build());

    // Sample 10: Completed year-end tax filing job.
    repository.save(JobExecution.builder()
        .jobName("Year-End Tax Filing - 2025")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(4).minusHours(8))
        .endTime(now.minusDays(4).minusHours(6))
        .message("Tax filings submitted for 1200 employees.")
        .build());

    // Sample 11: Completed leave encashment processing.
    repository.save(JobExecution.builder()
        .jobName("Leave Encashment - December 2025")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(5).minusHours(3))
        .endTime(now.minusDays(5).minusHours(2).minusMinutes(50))
        .message("Encashment processed for 340 employees.")
        .build());

    // Sample 12: Failed bank transfer job due to connectivity issue.
    repository.save(JobExecution.builder()
        .jobName("Bank Transfer - February Payroll")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(5).minusHours(1))
        .endTime(now.minusDays(5).minusMinutes(55))
        .message("Error: Bank API connection timeout after 3 retries.")
        .build());

    // Sample 13: Completed gratuity calculation job.
    repository.save(JobExecution.builder()
        .jobName("Gratuity Calculation - Annual")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(6).minusHours(4))
        .endTime(now.minusDays(6).minusHours(3).minusMinutes(45))
        .message("Gratuity computed for 1200 employees.")
        .build());

    // Sample 14: Completed salary revision job.
    repository.save(JobExecution.builder()
        .jobName("Salary Revision - Appraisal Cycle 2025")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(7).minusHours(5))
        .endTime(now.minusDays(7).minusHours(4).minusMinutes(40))
        .message("Salary revised for 890 employees.")
        .build());

    // Sample 15: Failed payslip generation due to template error.
    repository.save(JobExecution.builder()
        .jobName("Payslip Generation - January")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(8).minusHours(2))
        .endTime(now.minusDays(8).minusHours(1).minusMinutes(57))
        .message("Error: Payslip template missing for grade G7.")
        .build());

    // Sample 16: Completed ESI contribution processing.
    repository.save(JobExecution.builder()
        .jobName("ESI Contribution Processing - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(9).minusHours(3))
        .endTime(now.minusDays(9).minusHours(2).minusMinutes(55))
        .message("ESI contributions processed for 780 employees.")
        .build());

    // Sample 17: Completed professional tax deduction job.
    repository.save(JobExecution.builder()
        .jobName("Professional Tax Deduction - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(10).minusHours(2))
        .endTime(now.minusDays(10).minusHours(1).minusMinutes(53))
        .message("Professional tax deducted for 1100 employees.")
        .build());

    // Sample 18: Failed attendance sync job.
    repository.save(JobExecution.builder()
        .jobName("Attendance Data Sync - February")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(11).minusHours(1))
        .endTime(now.minusDays(11).minusMinutes(58))
        .message("Error: HRMS attendance feed unavailable.")
        .build());

    // Sample 19: Completed loan recovery processing.
    repository.save(JobExecution.builder()
        .jobName("Loan Recovery Deduction - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(12).minusHours(3))
        .endTime(now.minusDays(12).minusHours(2).minusMinutes(50))
        .message("Loan deductions applied for 210 employees.")
        .build());

    // Sample 20: Completed reimbursement processing.
    repository.save(JobExecution.builder()
        .jobName("Reimbursement Processing - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(13).minusHours(4))
        .endTime(now.minusDays(13).minusHours(3).minusMinutes(48))
        .message("Reimbursements processed for 430 employees.")
        .build());

    // Sample 21: Failed TDS filing job.
    repository.save(JobExecution.builder()
        .jobName("TDS Filing - Q3 2025")
        .status(JobStatus.FAILED)
        .startTime(now.minusDays(14).minusHours(6))
        .endTime(now.minusDays(14).minusHours(5).minusMinutes(55))
        .message("Error: TDS portal returned HTTP 503.")
        .build());

    // Sample 22: Completed new joiner onboarding payroll job.
    repository.save(JobExecution.builder()
        .jobName("New Joiner Payroll - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(15).minusHours(3))
        .endTime(now.minusDays(15).minusHours(2).minusMinutes(52))
        .message("Payroll processed for 45 new joiners.")
        .build());

    // Sample 23: Completed full-and-final settlement job.
    repository.save(JobExecution.builder()
        .jobName("Full & Final Settlement - February")
        .status(JobStatus.COMPLETED)
        .startTime(now.minusDays(16).minusHours(2))
        .endTime(now.minusDays(16).minusHours(1).minusMinutes(45))
        .message("F&F settlements completed for 18 separated employees.")
        .build());

    // Sample 24: Currently running quarterly audit job.
    repository.save(JobExecution.builder()
        .jobName("Quarterly Payroll Audit - Q1 2026")
        .status(JobStatus.RUNNING)
        .startTime(now.minusMinutes(12))
        .endTime(null)
        .message(null)
        .build());
  }
}
