package com.example.payroll_job_monitor_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Cross-Origin Resource Sharing (CORS) for the application.
 *
 * CORS is a browser security mechanism that blocks HTTP requests made from a
 * different origin (domain/port) than the server. This config explicitly allows
 * the Angular frontend (running on port 4200) to call the backend API.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /**
   * Registers CORS rules applied to all "/api/**" endpoints.
   *
   * Rules:
   * allowedOrigins - only the Angular dev server is permitted (localhost:4200)
   * allowedMethods - standard REST verbs plus OPTIONS (used for preflight)
   * allowedHeaders - all request headers are allowed
   * allowCredentials- cookies and auth headers are forwarded with cross-origin
   * requests
   *
   * @param registry the Spring MVC CORS registry
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**") // apply to all API routes
        .allowedOrigins("http://localhost:4200") // Angular frontend origin
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*") // accept any request header
        .allowCredentials(true); // allow cookies / auth tokens
  }
}
