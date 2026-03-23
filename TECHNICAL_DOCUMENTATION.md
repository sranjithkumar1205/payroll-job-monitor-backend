# Payroll Job Monitor — Backend Technical Documentation

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture Overview](#4-architecture-overview)
5. [Layered Architecture Detail](#5-layered-architecture-detail)
   - 5.1 [Entity Layer](#51-entity-layer)
   - 5.2 [Repository Layer](#52-repository-layer)
   - 5.3 [Service Layer](#53-service-layer)
   - 5.4 [Controller Layer](#54-controller-layer)
   - 5.5 [DTO Layer](#55-dto-layer)
   - 5.6 [Configuration Layer](#56-configuration-layer)
6. [API Contract](#6-api-contract)
7. [Request Flow — Trigger Job](#7-request-flow--trigger-job)
8. [Async Processing Flow](#8-async-processing-flow)
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
┌─────────────────────────────────────────────┐
│            Angular Frontend (port 4200)      │
└────────────────────┬────────────────────────┘
                     │ HTTP / multipart/form-data
                     ▼
┌─────────────────────────────────────────────┐
│         Spring Boot Backend (port 8080)      │
│                                             │
│  ┌────────────┐    ┌──────────────────────┐ │
│  │ JobController│──▶│ JobExecutionService  │ │
│  │  (REST API) │   │  (Business Logic)    │ │
│  └────────────┘   └──────────┬───────────┘ │
│                              │              │
│                   ┌──────────▼───────────┐  │
│                   │JobExecutionRepository│  │
│                   │   (Spring Data JPA)  │  │
│                   └──────────┬───────────┘  │
│                              │              │
│                   ┌──────────▼───────────┐  │
│                   │   H2 In-Memory DB    │  │
│                   │  (job_execution tbl) │  │
│                   └──────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  Async Thread Pool (JobThread-1..N)   │  │
│  │  Processes job in background          │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## 5. Layered Architecture Detail

### 5.1 Entity Layer

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

---

### 5.2 Repository Layer

**File:** `repository/JobExecutionRepository.java`

Extends `JpaRepository<JobExecution, Long>`, providing out-of-the-box:

| Method              | Description                                  |
|---------------------|----------------------------------------------|
| `save(entity)`      | Insert or update a job record                |
| `findById(id)`      | Fetch a single job by primary key            |
| `findAll()`         | Fetch all job records                        |

No custom queries are needed for the current feature set.

---

### 5.3 Service Layer

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

#### `getAllJobs()` / `getJobById(id)`
Simple repository delegation with entity-to-DTO mapping via the private `toResponse()` helper.

---

### 5.4 Controller Layer

**File:** `controller/JobController.java`

| Method | Path              | Description                                |
|--------|-------------------|--------------------------------------------|
| POST   | `/api/jobs/trigger` | Trigger a job; accepts `multipart/form-data` |
| GET    | `/api/jobs`         | List all job execution records             |
| GET    | `/api/jobs/{id}`    | Get a single job by ID                     |

All endpoints return **only DTO objects**, never JPA entities directly.
A `NoSuchElementException` from the service on `GET /api/jobs/{id}` is caught and translated to HTTP `404 Not Found`.

---

### 5.5 DTO Layer

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

### 5.6 Configuration Layer

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

## 6. API Contract

### POST `/api/jobs/trigger`

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

### GET `/api/jobs`

No request parameters.

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "jobName": "Payroll Job",
    "status": "COMPLETED",
    "startTime": "2026-03-22T10:00:00",
    "endTime": "2026-03-22T10:00:04",
    "message": "Job completed successfully"
  },
  {
    "id": 2,
    "jobName": "Tax Job",
    "status": "RUNNING",
    "startTime": "2026-03-22T10:01:00",
    "endTime": null,
    "message": "Job started successfully"
  }
]
```

---

### GET `/api/jobs/{id}`

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

## 7. Request Flow — Trigger Job

```
Angular Frontend
     │
     │  POST /api/jobs/trigger
     │  Content-Type: multipart/form-data
     │  Body: jobName=Payroll Job, file=(optional)
     ▼
JobController.triggerJob()
     │
     ├─ Delegates to ──▶ JobExecutionService.triggerJob()
     │                        │
     │                        ├─ 1. Log file name (if file present)
     │                        │
     │                        ├─ 2. Build JobExecution entity
     │                        │      status    = RUNNING
     │                        │      startTime = now()
     │                        │      message   = "Job started successfully"
     │                        │
     │                        ├─ 3. Save to H2 DB  →  id assigned
     │                        │
     │                        ├─ 4. Dispatch processJobAsync(id, file)
     │                        │      (returns immediately, runs on JobThread-N)
     │                        │
     │                        └─ 5. Map entity → JobExecutionResponse DTO
     │
     └─ Return HTTP 200 with JobExecutionResponse (status=RUNNING)
```

---

## 8. Async Processing Flow

```
JobThread-N (background)
     │
     ├─ Sleep 3–5 seconds (simulated processing)
     │
     ├─ If file present → log simulated file processing
     │
     ├─ Re-fetch JobExecution from DB by id
     │
     ├─ [SUCCESS path]
     │     status   = COMPLETED
     │     endTime  = now()
     │     message  = "Job completed successfully"
     │     Save to DB
     │
     ├─ [InterruptedException path]
     │     Restore interrupt flag
     │     status   = FAILED
     │     endTime  = now()
     │     message  = "Job interrupted: <reason>"
     │     Save to DB
     │
     └─ [General Exception path]
           status   = FAILED
           endTime  = now()
           message  = "Job failed: <exception message>"
           Save to DB
```

> The frontend dashboard can poll `GET /api/jobs` or `GET /api/jobs/{id}` to observe RUNNING → COMPLETED/FAILED transitions.

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
| All jobs listed                   | 200 OK      | Array of DTOs              |
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

# Get all jobs
curl http://localhost:8080/api/jobs

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
