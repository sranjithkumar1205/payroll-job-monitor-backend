package com.example.payroll_job_monitor_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous job processing.
 *
 * @EnableAsync - activates Spring's @Async annotation support so that methods
 *              annotated with @Async run on a managed thread pool instead of
 *              the caller's thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Defines the thread pool used by all @Async methods in the application.
   * Named "jobTaskExecutor" so Spring resolves it automatically when @Async
   * methods do not specify an executor.
   *
   * Pool settings:
   * corePoolSize = 5 : number of threads kept alive even when idle
   * maxPoolSize = 10 : maximum threads created under high load
   * queueCapacity = 25 : tasks queued before new threads are spawned
   * threadPrefix : prefix for thread names (visible in logs & thread dumps)
   *
   * @return configured Executor for async job processing
   */
  @Bean(name = "jobTaskExecutor")
  public Executor jobTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5); // always keep 5 threads ready
    executor.setMaxPoolSize(10); // grow up to 10 threads under load
    executor.setQueueCapacity(25); // buffer up to 25 waiting tasks
    executor.setThreadNamePrefix("JobThread-"); // e.g. "JobThread-1", "JobThread-2"
    executor.initialize();
    return executor;
  }
}
