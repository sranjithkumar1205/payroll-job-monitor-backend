package com.example.payroll_job_monitor_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Payroll Job Monitor backend application.
 *
 * @SpringBootApplication enables auto-configuration, component scanning,
 *                        and configuration support for the entire application.
 */
@SpringBootApplication
public class PayrollJobMonitorBackendApplication {

	/**
	 * Bootstraps and launches the Spring Boot application.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(PayrollJobMonitorBackendApplication.class, args);
	}

}
