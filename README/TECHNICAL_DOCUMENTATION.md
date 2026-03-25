# Payroll Job Monitor — Backend Technical Documentation

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture Overview](#4-architecture-overview)
5. [Application Startup Sequence](#5-application-startup-sequence)
6. [Layered Architecture Detail](#6-layered-architecture-detail)
   - 6.1 [Entity Layer](#61-entity-layer)
   - 6.2 [Repository Layer](#62-repository-layer)
   - 6.3 [Service Layer](#63-service-layer)
   - 6.4 [Controller Layer](#64-controller-layer)
   - 6.5 [DTO Layer](#65-dto-layer)
   - 6.6 [Configuration Layer](#66-configuration-layer)
7. [End-to-End Flow Diagrams](#7-end-to-end-flow-diagrams)
   - 7.1 [Full Application Lifecycle](#71-full-application-lifecycle)
   - 7.2 [Trigger Job Flow](#72-trigger-job-flow)
   - 7.3 [Async Processing Flow](#73-async-processing-flow)
   - 7.4 [Poll / Read Flow](#74-poll--read-flow)
8. [API Contract](#8-api-contract)
   - 8.1 [POST /api/jobs/trigger](#81-post-apijobstrigger)
   - 8.2 [GET /api/jobs (Paginated)](#82-get-apijobs-paginated)
   - 8.3 [GET /api/jobs/{id}](#83-get-apijobsid)
9. [Database Schema](#9-database-schema)
10. [Configuration Reference](#10-configuration-reference)
11. [CORS Policy](#11-cors-policy)
12. [Error Handling](#12-error-handling)
13. [Running the Application](#13-running-the-application)

---

## 1. Project Overview

**payroll-job-monitor-backend** is a Spring Boot REST API that powers the Angular frontend `payroll-job-monitor-ui`. It allows users to:

- **Trigger** payroll jobs (with or without a file upload)
- **Monitor** job execution status in real time (RUNNING → COMPLETED / FAILED)
- **Query** individual or all job execution records

Jobs are executed asynchronously so the API responds immediately while processing continues in the background.

---

## 2. Technology Stack

| Component          | Technology              | Version  |
|--------------------|-------------------------|----------|
| Language           | Java                    | 21       |
| Framework          | Spring Boot             | 4.0.4    |
| ORM                | Spring Data JPA / Hibernate | 7.x  |
| Database           | H2 (in-memory)          | 2.4.x    |
| JSON               | Jackson (annotations 2.x, core 3.x) | —  |
| Boilerplate        | Lombok                  | —        |
| Build Tool         | Maven (Maven Wrapper)   | —        |
| Server             | Embedded Tomcat         | —        |

---

## 3. Project Structure

```
payroll-job-monitor-backend/
├── src/
│   └── main/
│       ├── java/com/example/payroll_job_monitor_backend/
│       │   ├── PayrollJobMonitorBackendApplication.java   ← Entry point
│       │   ├── config/
│       │   │   ├── AsyncConfig.java      ← @EnableAsync + thread pool
│       │   │   ├── CorsConfig.java       ← CORS rules for frontend
│       │   │   └── JacksonConfig.java    ← JSON serialization config
│       │   ├── controller/
│       │   │   └── JobController.java    ← REST endpoints
│       │   ├── dto/
│       │   │   └── JobExecutionResponse.java  ← API response shape
│       │   ├── entity/
│       │   │   ├── JobExecution.java     ← JPA entity (DB table)
│       │   │   └── JobStatus.java        ← Enum: RUNNING/COMPLETED/FAILED
│       │   ├── repository/
│       │   │   └── JobExecutionRepository.java  ← DB access layer
│       │   └── service/
│       │       └── JobExecutionService.java  ← Business logic
│       └── resources/
│           └── application.properties    ← App configuration
├── pom.xml
└── TECHNICAL_DOCUMENTATION.md
```

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                  Angular Frontend (port 4200)                    │
│        (Triggers jobs, polls status, displays results)          │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP / multipart/form-data
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Spring Boot Backend (port 8080)                 │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   HTTP Layer (CorsConfig)                │   │
│  │          Allows requests from http://localhost:4200      │   │
│  └──────────────────────────┬──────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐   │
│  │                 JobController  /api/jobs                 │   │
│  │   POST /trigger  │  GET /       │  GET /{id}             │   │
│  └──────────────────────────┬──────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐   │
│  │               JobExecutionService (Business Logic)       │   │
│  │    triggerJob()  │  getAllJobs()  │  getJobById()         │   │
│  │         │               │               │                │   │
│  │         ▼               │               │                │   │
│  │  processJobAsync()      │               │                │   │
│  │  (@Async — background)  │               │                │   │
│  └──────────┬──────────────┴───────────────┘                │   │
│             │                                               │   │
│  ┌──────────▼──────────────────────────────────────────┐   │   │
│  │            JobExecutionRepository (Spring Data JPA)  │   │   │
│  │       save() │ findById() │ findAll(Pageable)         │   │   │
│  └──────────────────────────┬────────────────────────────┘   │   │
│                             │                                   │
│  ┌──────────────────────────▼──────────────────────────────┐   │
│  │                H2 In-Memory Database                     │   │
│  │             Table: job_execution                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │            Async Thread Pool (AsyncConfig)               │   │
│  │    JobThread-1 … JobThread-10  |  Queue: up to 25 tasks  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Application Startup Sequence

When the Spring Boot application starts, the following events occur in order:

```
JVM starts
     │
     ▼
Spring Boot auto-configuration
     │  ─ Loads application.properties
     │  ─ Configures H2 DataSource (in-memory, jdbc:h2:mem:payrolldb)
     │  ─ Creates JPA EntityManagerFactory
     │  ─ Runs Hibernate DDL: CREATE TABLE job_execution (...)
     │  ─ Initializes Bean: JobExecutionRepository
     │  ─ Initializes Bean: JobExecutionService
     │  ─ Initializes Bean: JobController
     │  ─ Initializes Bean: AsyncConfig → creates jobTaskExecutor thread pool
     │                         (5 core threads, max 10, queue 25)
     │  ─ Initializes Bean: CorsConfig  → registers CORS rules
     │
     ▼
CommandLineRunner.run() → DataInitializer.run()
     │
     │  Inserts 4 sample records into job_execution:
     │    1. "Payroll Processing - January"   → COMPLETED  (3 hrs ago)
     │    2. "Payroll Processing - February"  → COMPLETED  (2 hrs ago)
     │    3. "Tax Report Generation"          → FAILED     (45 min ago)
     │    4. "Payroll Processing - March"     → RUNNING    (5 min ago)
     │
     ▼
Embedded Tomcat starts on port 8080
     │
     ▼
Application ready — HTTP requests accepted
```

> **Note:** The database uses `ddl-auto=create-drop`, meaning all schema and data are recreated fresh on every startup and permanently lost on shutdown.

---

## 6. Layered Architecture Detail

### 6.1 Entity Layer

**File:** `entity/JobExecution.java`

Maps directly to the `job_execution` table in H2.

| Field       | Java Type       | DB Column      | Nullable | Notes                          |
|-------------|-----------------|----------------|----------|--------------------------------|
| `id`        | `Long`          | `id` BIGINT    | No       | Auto-generated primary key     |
| `jobName`   | `String`        | `job_name`     | No       | Name of the job submitted      |
| `status`    | `JobStatus`     | `status` ENUM  | No       | RUNNING / COMPLETED / FAILED   |
| `startTime` | `LocalDateTime` | `start_time`   | Yes      | Set when job is triggered      |
| `endTime`   | `LocalDateTime` | `end_time`     | Yes      | Set when job finishes          |
| `message`   | `String`        | `message`      | Yes      | Human-readable status message  |

**File:** `entity/JobStatus.java`

```
RUNNING   → Job has been triggered and is processing
COMPLETED → Job finished successfully
FAILED    → Job encountered an error
```

Status transitions:

```
              ┌─────────┐
   trigger()  │         │
 ────────────▶│ RUNNING │
              │         │
              └────┬────┘
                   │
       ┌───────────┴───────────┐
       │                       │
       ▼                       ▼
  ┌─────────┐            ┌────────┐
  │COMPLETED│            │ FAILED │
  └─────────┘            └────────┘
  (success path)        (error/interrupt)
```

---

### 6.2 Repository Layer

**File:** `repository/JobExecutionRepository.java`

Extends `JpaRepository<JobExecution, Long>`, providing out-of-the-box:

| Method              | Description                                  |
|---------------------|----------------------------------------------|
| `save(entity)`      | Insert or update a job record                |
| `findById(id)`      | Fetch a single job by primary key            |
| `findAll()`         | Fetch all job records                        |

No custom queries are needed for the current feature set.

---

### 6.3 Service Layer

**File:** `service/JobExecutionService.java`

Contains all business logic. Three public methods:

#### `triggerJob(jobName, file)`
1. Logs the uploaded filename if a file is present.
2. Builds a `JobExecution` entity with `status = RUNNING`, `startTime = now()`, and `message = "Job started successfully"`.
3. Persists the entity to the database immediately (so the record exists before async work starts).
4. Fires `processJobAsync(id, file)` — returns immediately to the caller.
5. Returns the saved entity mapped to `JobExecutionResponse`.

#### `processJobAsync(jobId, file)` — `@Async`
Runs on a background thread from the `jobTaskExecutor` pool:
1. Simulates processing with a random 3–5 second `Thread.sleep()`.
2. If a file was supplied, logs that file processing is simulated.
3. Re-fetches the record from the DB by `jobId`.
4. On success → sets `status = COMPLETED`, `endTime = now()`, `message = "Job completed successfully"`, saves.
5. On `InterruptedException` → calls `markJobFailed()` and restores the interrupt flag.
6. On any other exception → calls `markJobFailed()` with the exception message.

#### `getAllJobs(pageable)` / `getAllJobs()` / `getJobById(id)`
- `getAllJobs(Pageable)` — delegates to `jobExecutionRepository.findAll(Pageable)` (built into `JpaRepository`) and maps each entity to a DTO. Returns a Spring `Page<JobExecutionResponse>` containing the requested slice plus metadata (`totalElements`, `totalPages`, etc.).
- `getAllJobs()` — original non-paginated overload; kept for internal use.
- `getJobById(id)` — simple repository delegation with entity-to-DTO mapping via the private `toResponse()` helper.

#### Private helper: `toResponse(JobExecution)`
Converts a `JobExecution` JPA entity into a `JobExecutionResponse` DTO. This ensures the HTTP response shape is never coupled to the database model.

#### Private helper: `markJobFailed(jobId, errorMessage)`
Called from the catch blocks of `processJobAsync()`. Re-fetches the entity and persists `status=FAILED`, `endTime=now()`, and the error message.

---

### 6.4 Controller Layer

**File:** `controller/JobController.java`

| Method | Path                | Description                                          |
|--------|---------------------|------------------------------------------------------|
| POST   | `/api/jobs/trigger` | Trigger a job; accepts `multipart/form-data`         |
| GET    | `/api/jobs`         | List job executions — paginated, sorted, filterable  |
| GET    | `/api/jobs/{id}`    | Get a single job by ID                               |

All endpoints return **only DTO objects**, never JPA entities directly.
A `NoSuchElementException` from the service on `GET /api/jobs/{id}` is caught and translated to HTTP `404 Not Found`.

---

### 6.5 DTO Layer

**File:** `dto/JobExecutionResponse.java`

This is the single response shape returned to the Angular frontend for all three endpoints.

```json
{
  "id": 1,
  "jobName": "Payroll Job",
  "status": "RUNNING",
  "startTime": "2026-03-22T10:00:00",
  "endTime": null,
  "message": "Job started successfully"
}
```

`@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")` is applied to both `startTime` and `endTime` to serialize `LocalDateTime` in ISO-8601 format without milliseconds or timezone offsets.

---

### 6.6 Configuration Layer

#### `AsyncConfig.java`
- Annotated with `@EnableAsync` to activate Spring's async execution infrastructure.
- Defines a `ThreadPoolTaskExecutor` bean named `jobTaskExecutor`:

| Property         | Value        |
|------------------|--------------|
| Core pool size   | 5 threads    |
| Max pool size    | 10 threads   |
| Queue capacity   | 25 tasks     |
| Thread name prefix | `JobThread-` |

#### `CorsConfig.java`
Implements `WebMvcConfigurer` to allow cross-origin requests from the Angular dev server. See [Section 11](#11-cors-policy).

#### `JacksonConfig.java`
Placeholder configuration class. Date/time serialization format is controlled per-field via `@JsonFormat` annotations on the DTO.

---

## 7. End-to-End Flow Diagrams

### 7.1 Full Application Lifecycle

```
  ┌───────────────────────────────────────────────────────────────────────┐
  │  STARTUP                                                              │
  │  Spring Boot starts → Hibernate creates schema → DataInitializer      │
  │  seeds 4 records (2 COMPLETED, 1 FAILED, 1 RUNNING)                  │
  └──────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
  ┌───────────────────────────────────────────────────────────────────────┐
  │  IDLE  — Server listens on :8080, thread pool is ready               │
  └──────────────────────────────────┬────────────────────────────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
              ▼                      ▼                      ▼
  POST /api/jobs/trigger    GET /api/jobs             GET /api/jobs/{id}
  (triggers new job)        (poll all statuses)       (poll one status)
              │                      │                      │
              ▼                      │                      │
  job saved: RUNNING                 │                      │
  async task dispatched              │                      │
              │                      │                      │
              ▼                      ▼                      ▼
  [JobThread-N] processes       DB read →             DB read by id →
  3–5s, updates to              Page<DTO>             DTO or 404
  COMPLETED / FAILED
              │
              ▼
  ┌───────────────────────────────────────────────────────────────────────┐
  │  SHUTDOWN — Hibernate drops schema, all in-memory data is lost       │
  └───────────────────────────────────────────────────────────────────────┘
```

---

### 7.2 Trigger Job Flow

```
Angular Frontend
     │
     │  POST /api/jobs/trigger
     │  Content-Type: multipart/form-data
     │  Body: jobName=Payroll Job, file=(optional)
     ▼
JobController.triggerJob()
     │
     └──▶ JobExecutionService.triggerJob()
               │
               ├─ 1. Log file name (if file present)
               │
               ├─ 2. Build JobExecution entity
               │      status    = RUNNING
               │      startTime = now()
               │      message   = "Job started successfully"
               │
               ├─ 3. jobExecutionRepository.save(entity)  →  id assigned
               │
               ├─ 4. Dispatch processJobAsync(id, file)
               │      (non-blocking; runs on JobThread-N)
               │
               └─ 5. toResponse(saved)  →  JobExecutionResponse DTO
     │
     └── HTTP 200 OK → { id, jobName, status:"RUNNING", startTime, ... }
```

---

### 7.3 Async Processing Flow

```
JobThread-N (background — jobTaskExecutor pool)
     │
     ├─ Sleep random 3–5 seconds  (simulates real processing)
     │
     ├─ [Optional] Log simulated file processing
     │
     ├─ jobExecutionRepository.findById(jobId)
     │      (re-fetch avoids stale state from the triggering thread)
     │
     ├─ [SUCCESS path]
     │     job.status   = COMPLETED
     │     job.endTime  = now()
     │     job.message  = "Job completed successfully"
     │     jobExecutionRepository.save(job)
     │     log.info("Job id={} completed successfully")
     │
     ├─ [InterruptedException]
     │     Thread.currentThread().interrupt()   ← restore interrupt flag
     │     markJobFailed(jobId, "Job interrupted: <reason>")
     │
     └─ [General Exception]
           log.error("Job id={} failed: ...")
           markJobFailed(jobId, "Job failed: <exception message>")


markJobFailed(jobId, errorMessage):
     jobExecutionRepository.findById(jobId).ifPresent(job -> {
         job.status  = FAILED
         job.endTime = now()
         job.message = errorMessage
         save(job)
     })
```

---

### 7.4 Poll / Read Flow

The Angular frontend polls the job list or individual records to observe status transitions:

```
Angular Frontend
     │
     │  GET /api/jobs?page=0&size=10&sortBy=startTime&sortDir=desc
     ▼
JobController.getAllJobs()
     │
     └──▶ JobExecutionService.getAllJobs(Pageable)
               │
               └──▶ jobExecutionRepository.findAll(Pageable)
                         │
                         └──▶ SELECT * FROM job_execution
                              ORDER BY start_time DESC
                              LIMIT 10 OFFSET 0
                         │
                         ▼
               Page<JobExecution>  →  .map(toResponse)  →  Page<JobExecutionResponse>
     │
     └── HTTP 200 OK → { content:[...], totalElements, totalPages, ... }


For a single record:

     GET /api/jobs/{id}
     └──▶ service.getJobById(id)
               └──▶ findById(id).orElseThrow(NoSuchElementException)
                         found   → 200 OK with DTO
                         missing → NoSuchElementException → 404 Not Found
```

---

## 8. API Contract

### 8.1 POST `/api/jobs/trigger`

**Content-Type:** `multipart/form-data`

| Parameter | Type          | Required | Description                    |
|-----------|---------------|----------|--------------------------------|
| `jobName` | String        | Yes      | Name of the job to execute     |
| `file`    | MultipartFile | No       | Optional file for the job      |

**Response `200 OK`:**
```json
{
  "id": 1,
  "jobName": "Payroll Job",
  "status": "RUNNING",
  "startTime": "2026-03-22T10:00:00",
  "endTime": null,
  "message": "Job started successfully"
}
```

---

### 8.2 GET `/api/jobs` (Paginated)

#### Query Parameters

| Parameter | Type   | Default     | Description                                                                         |
|-----------|--------|-------------|--------------------------------------------------------------------------------------|
| `page`    | int    | `0`         | Zero-based page index                                                                |
| `size`    | int    | `10`        | Number of records per page                                                           |
| `sortBy`  | String | `startTime` | Field name to sort by (`id`, `jobName`, `startTime`, `endTime`, `status`, `message`) |
| `sortDir` | String | `desc`      | Sort direction: `asc` or `desc`                                                      |

#### Example Requests

```
GET /api/jobs                              → page 0, 10 items, sorted by startTime desc
GET /api/jobs?page=1&size=5               → page 2 (zero-based), 5 items per page
GET /api/jobs?sortBy=jobName&sortDir=asc  → sorted alphabetically by job name
GET /api/jobs?page=0&size=20&sortBy=id    → first 20 records sorted by ID descending
```

#### Response `200 OK` — with data

```json
{
  "content": [
    {
      "id": 2,
      "jobName": "Tax Job",
      "status": "RUNNING",
      "startTime": "2026-03-22T10:01:00",
      "endTime": null,
      "message": "Job started successfully"
    },
    {
      "id": 1,
      "jobName": "Payroll Job",
      "status": "COMPLETED",
      "startTime": "2026-03-22T10:00:00",
      "endTime": "2026-03-22T10:00:04",
      "message": "Job completed successfully"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true,
  "empty": false
}
```

#### Response `200 OK` — no data (empty database)

When there are no job records, the API still returns `200 OK` with an empty page — **never a 404 or error**.

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true,
  "empty": true
}
```

Frontends should check `empty: true` or `totalElements === 0` to display a "No jobs found" state.

---

### 8.3 GET `/api/jobs/{id}`

| Path Variable | Type | Description        |
|---------------|------|--------------------|
| `id`          | Long | Job execution ID   |

**Response `200 OK`:**
```json
{
  "id": 1,
  "jobName": "Payroll Job",
  "status": "COMPLETED",
  "startTime": "2026-03-22T10:00:00",
  "endTime": "2026-03-22T10:00:04",
  "message": "Job completed successfully"
}
```

**Response `404 Not Found`:** when the given `id` does not exist.

---

## 9. Database Schema

Database: **H2 in-memory** (`jdbc:h2:mem:payrolldb`)  
DDL mode: `create-drop` (schema created on startup, dropped on shutdown)

```sql
CREATE TABLE job_execution (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    job_name   VARCHAR(255)                              NOT NULL,
    status     ENUM('RUNNING', 'COMPLETED', 'FAILED')   NOT NULL,
    start_time TIMESTAMP(6),
    end_time   TIMESTAMP(6),
    message    VARCHAR(255)
);
```

**H2 Console** is available at:  
`http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:mem:payrolldb` | Username: `sa` | Password: *(empty)*

---

## 10. Configuration Reference

**File:** `src/main/resources/application.properties`

```properties
# Application
spring.application.name=payroll-job-monitor-backend
server.port=8080

# H2 In-Memory Database
spring.datasource.url=jdbc:h2:mem:payrolldb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

# Multipart File Upload
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

| Property | Purpose |
|---|---|
| `DB_CLOSE_DELAY=-1` | Keep H2 DB alive as long as the JVM runs |
| `ddl-auto=create-drop` | Auto-create schema on start, drop on stop |
| `open-in-view=false` | Prevents lazy-loading during view rendering (best practice) |
| `max-file-size=10MB` | Maximum size of an uploaded file |

---

## 11. CORS Policy

Configured in `CorsConfig.java`:

| Setting           | Value                     |
|-------------------|---------------------------|
| Allowed Origins   | `http://localhost:4200`   |
| Allowed Methods   | GET, POST, PUT, DELETE, OPTIONS |
| Allowed Headers   | `*` (all headers)         |
| Allow Credentials | true                      |
| Path Pattern      | `/api/**`                 |

This permits the Angular development server to call all `/api/` endpoints without browser CORS errors.

---

## 12. Error Handling

| Scenario                          | HTTP Status | Response body              |
|-----------------------------------|-------------|----------------------------|
| Job triggered successfully        | 200 OK      | `JobExecutionResponse` DTO |
| All jobs listed (with data)        | 200 OK      | Paginated `Page<JobExecutionResponse>` — `empty: false`                    |
| All jobs listed (no data)          | 200 OK      | Paginated `Page<JobExecutionResponse>` — `empty: true`, `totalElements: 0` |
| Job found by ID                   | 200 OK      | `JobExecutionResponse` DTO |
| Job not found (`GET /api/jobs/{id}`) | 404 Not Found | Empty body              |
| Background job fails              | — (no HTTP) | DB record updated: `status=FAILED`, `message` contains error details |

---

## 13. Running the Application

### Prerequisites
- Java 21 JDK installed
- No external database required (uses embedded H2)

### Start the server

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

### Verify endpoints

```bash
# Trigger a job (no file)
curl -X POST http://localhost:8080/api/jobs/trigger \
     -F "jobName=Payroll Job"

# Trigger a job with a file
curl -X POST http://localhost:8080/api/jobs/trigger \
     -F "jobName=Payroll Job" \
     -F "file=@/path/to/payroll.csv"

# Get all jobs (defaults: page 0, size 10, sorted by startTime desc)
curl http://localhost:8080/api/jobs

# Get page 2 with 5 items per page
curl "http://localhost:8080/api/jobs?page=1&size=5"

# Sort by jobName ascending
curl "http://localhost:8080/api/jobs?sortBy=jobName&sortDir=asc"

# Get job by ID
curl http://localhost:8080/api/jobs/1
```

### H2 Console (browser)

```
URL      : http://localhost:8080/h2-console
JDBC URL : jdbc:h2:mem:payrolldb
Username : sa
Password : (leave blank)
```

### Run tests

```bash
.\mvnw.cmd test
```
