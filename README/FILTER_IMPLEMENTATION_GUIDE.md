# Job Name Search & Status Filter — Implementation Guide

## Table of Contents

1. [Feature Overview](#1-feature-overview)
2. [Project Structure — Before vs After](#2-project-structure--before-vs-after)
3. [Implementation Steps (What Was Done and Why)](#3-implementation-steps-what-was-done-and-why)
4. [Approaches Tried — What Did Not Work](#4-approaches-tried--what-did-not-work)
5. [Approach That Worked](#5-approach-that-worked)
6. [File-by-File Code Walkthrough](#6-file-by-file-code-walkthrough)
7. [Request Flow — End to End](#7-request-flow--end-to-end)
8. [API Reference](#8-api-reference)
9. [Error Handling](#9-error-handling)
10. [Test Coverage](#10-test-coverage)
11. [Key Concepts to Understand](#11-key-concepts-to-understand)

---

## 1. Feature Overview

Before this change, `GET /api/jobs` returned **all records** from the database with only
pagination and sorting. There was no way for the frontend to limit results to a specific
job name or a specific status.

**After this change**, the same endpoint accepts two optional query parameters:

| Parameter | Type   | Behaviour |
|-----------|--------|-----------|
| `jobName` | string | Case-insensitive partial match (`LIKE %value%`) |
| `status`  | string | Exact enum match (`RUNNING`, `COMPLETED`, `FAILED`) |

Both parameters are **optional**. When omitted, the endpoint works exactly as before —
existing callers are not broken.

Example request using both filters:

```
GET /api/jobs?page=0&size=10&sortBy=startTime&sortDir=desc&jobName=payroll&status=COMPLETED
```

---

## 2. Project Structure — Before vs After

### Before

```
src/main/java/com/example/payroll_job_monitor_backend/
├── controller/
│   └── JobController.java            ← no filter params
├── dto/
│   └── JobExecutionResponse.java
├── entity/
│   ├── JobExecution.java
│   └── JobStatus.java
├── repository/
│   └── JobExecutionRepository.java   ← only JpaRepository
├── service/
│   └── JobExecutionService.java      ← no filtering logic
└── config/
    └── ...
```

### After (new files are marked ✨)

```
src/main/java/com/example/payroll_job_monitor_backend/
├── controller/
│   └── JobController.java            ← extended with jobName + status params
├── dto/
│   └── JobExecutionResponse.java     ← unchanged
├── entity/
│   ├── JobExecution.java             ← unchanged
│   └── JobStatus.java                ← unchanged
├── exception/                        ✨ new package
│   ├── BadRequestException.java      ✨ new file
│   └── GlobalExceptionHandler.java   ✨ new file
├── repository/
│   └── JobExecutionRepository.java   ← + JpaSpecificationExecutor
├── service/
│   └── JobExecutionService.java      ← + getAllJobs(jobName, status, pageable)
├── specification/                    ✨ new package
│   └── JobSpecification.java         ✨ new file
└── config/
    └── ...

src/test/ (new test files ✨)
├── service/
│   └── JobExecutionServiceFilterTest.java   ✨ 11 unit tests
└── controller/
    └── JobControllerFilterTest.java         ✨ 7 MockMvc tests
```

---

## 3. Implementation Steps (What Was Done and Why)

### Step 1 — Read and understand the existing code

Before writing a single line, every existing file was read:

- `JobExecution` entity → identified the `jobName` (String) and `status` (JobStatus enum) fields
- `JobStatus` enum → noted the three values: `RUNNING`, `COMPLETED`, `FAILED`
- `JobExecutionRepository` → confirmed it only extended `JpaRepository` (no dynamic query support)
- `JobExecutionService.getAllJobs(Pageable)` → understood the existing paginated response
- `JobController.getAllJobs(...)` → noted only four params: `page`, `size`, `sortBy`, `sortDir`
- `pom.xml` → confirmed Spring Boot version is **4.0.4** (important for test annotations — see Step 6)

This reading phase prevented mistakes like overwriting working methods or missing
existing behaviour.

---

### Step 2 — Add `JpaSpecificationExecutor` to the repository

**File:** [repository/JobExecutionRepository.java](src/main/java/com/example/payroll_job_monitor_backend/repository/JobExecutionRepository.java)

**Why:** `JpaRepository` only supports `findAll(Pageable)` — it always fetches all rows.
To filter dynamically, the repository needs to understand `Specification<T>` objects.
`JpaSpecificationExecutor` adds the `findAll(Specification, Pageable)` method.

**Change:**
```java
// Before
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {}

// After
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long>,
        JpaSpecificationExecutor<JobExecution> {}
```

No other change was needed in this file. Spring Data JPA automatically provides the
implementation at runtime.

---

### Step 3 — Create `JobSpecification` (the filter predicate builder)

**File:** [specification/JobSpecification.java](src/main/java/com/example/payroll_job_monitor_backend/specification/JobSpecification.java)

**Why:** Filter logic (SQL predicates) should **not** live inside the service or
repository. A dedicated specification class keeps the logic reusable, testable, and
isolated.

**What it does:**

```java
public static Specification<JobExecution> withFilters(String jobName, JobStatus status) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        // jobName predicate — only added when jobName is non-blank
        if (jobName != null && !jobName.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("jobName")), "%" + jobName.trim().toLowerCase() + "%")
            );
        }

        // status predicate — only added when status enum is non-null
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        // Combine all present predicates with AND
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

Key points:
- The lambda `(root, query, cb)` is the standard JPA Criteria API callback
- `root` represents the `FROM job_execution` table alias
- `cb` (CriteriaBuilder) is the factory for SQL expressions like `LOWER()`, `LIKE`, `=`
- When neither filter is provided, `predicates` is empty and `cb.and()` with an empty
  array produces a no-op predicate — equivalent to `WHERE 1=1` — so all rows are returned
- The class is `final` with a private constructor because it is a stateless utility

---

### Step 4 — Create `BadRequestException`

**File:** [exception/BadRequestException.java](src/main/java/com/example/payroll_job_monitor_backend/exception/BadRequestException.java)

**Why:** When a client sends `?status=FOOBAR`, the application must reject it with
HTTP 400. Instead of catching and converting errors in the controller (which creates
coupling), the service throws a dedicated exception and a central handler translates
it into the right HTTP response.

```java
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

It is a `RuntimeException` (unchecked), so callers do not need `try-catch` blocks or
`throws` declarations.

---

### Step 5 — Create `GlobalExceptionHandler`

**File:** [exception/GlobalExceptionHandler.java](src/main/java/com/example/payroll_job_monitor_backend/exception/GlobalExceptionHandler.java)

**Why:** A centralised handler using `@RestControllerAdvice` intercepts exceptions from
any controller and maps them to HTTP responses. This avoids duplicating
`try { } catch { }` blocks in every controller method.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
```

HTTP response for an invalid status:
```json
HTTP 400 Bad Request
{
  "error": "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED"
}
```

---

### Step 6 — Update `JobExecutionService`

**File:** [service/JobExecutionService.java](src/main/java/com/example/payroll_job_monitor_backend/service/JobExecutionService.java)

**What was added:**

```java
public Page<JobExecutionResponse> getAllJobs(String jobName, String statusStr, Pageable pageable) {
    // 1. Normalise jobName — treat blank as null (no filter)
    String trimmedName = (jobName != null && !jobName.isBlank()) ? jobName.trim() : null;

    // 2. Parse statusStr to enum — throw BadRequestException for invalid values
    JobStatus statusEnum = null;
    if (statusStr != null && !statusStr.isBlank()) {
        try {
            statusEnum = JobStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(JobStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid status value. Allowed values: " + allowed);
        }
    }

    // 3. Build the Specification and query
    Specification<JobExecution> spec = JobSpecification.withFilters(trimmedName, statusEnum);
    return jobExecutionRepository.findAll(spec, pageable).map(this::toResponse);
}

// Backward-compatible overload — existing callers still work
public Page<JobExecutionResponse> getAllJobs(Pageable pageable) {
    return getAllJobs(null, null, pageable);
}
```

**Responsibilities of this method:**
- Input normalisation (trim, blank check)
- Enum validation with a meaningful error message
- Delegating to `JobSpecification` for predicate building
- Delegating to the repository for actual database query
- Mapping entity results to DTOs

The old `getAllJobs(Pageable)` overload now calls the new method with `null` filters,
so it is backward-compatible without duplicating any code.

---

### Step 7 — Update `JobController`

**File:** [controller/JobController.java](src/main/java/com/example/payroll_job_monitor_backend/controller/JobController.java)

Two new `@RequestParam` annotations were added, both `required = false` (optional):

```java
@GetMapping
public ResponseEntity<Page<JobExecutionResponse>> getAllJobs(
        @RequestParam(defaultValue = "0")   int page,
        @RequestParam(defaultValue = "10")  int size,
        @RequestParam(defaultValue = "startTime") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir,
        @RequestParam(required = false) String jobName,   // ← new
        @RequestParam(required = false) String status) {  // ← new
    Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending()
                                                : Sort.by(sortBy).descending();
    PageRequest pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(jobExecutionService.getAllJobs(jobName, status, pageable));
}
```

The controller's responsibility is **only** parameter parsing and HTTP binding.
No validation or filter logic lives here — that belongs in the service.

---

### Step 8 — Write tests

Two new test files were created:

- [service/JobExecutionServiceFilterTest.java](src/test/java/com/example/payroll_job_monitor_backend/service/JobExecutionServiceFilterTest.java) — 11 Mockito unit tests
- [controller/JobControllerFilterTest.java](src/test/java/com/example/payroll_job_monitor_backend/controller/JobControllerFilterTest.java) — 7 MockMvc slice tests

See [Section 10](#10-test-coverage) for the full test list.

---

## 4. Approaches Tried — What Did Not Work

### ❌ Approach 1: Using `@WebMvcTest` from the old package

**What was tried:**
```java
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
```

**Why it failed:**
This import path works in Spring Boot 2 and 3. In Spring Boot **4.0.x**, the Web MVC
test slice was moved to a separate module (`spring-boot-webmvc-test`) with a **new
package path**.

**Exact error:**
```
[ERROR] package org.springframework.boot.test.autoconfigure.web.servlet does not exist
[ERROR] cannot find symbol: class WebMvcTest
```

**Root cause investigation:**
The contents of the `spring-boot-webmvc-test-4.0.4.jar` were inspected directly using
PowerShell's ZIP file API:
```powershell
$zip.Entries | Select-Object -ExpandProperty FullName | Select-String "WebMvc"
```
This revealed the class now lives at:
```
org/springframework/boot/webmvc/test/autoconfigure/WebMvcTest.class
```

**Fix applied:**
```java
// Wrong (Spring Boot 2/3)
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

// Correct (Spring Boot 4)
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

---

### ❌ Approach 2: Using `@MockBean` (deprecated in Spring Boot 4)

**What was considered:**
`@MockBean` was the standard annotation for mocking Spring beans in tests in Spring Boot
2 and 3.

**Why it was not used:**
In Spring Boot 4 / Spring Framework 7, `@MockBean` was replaced by
`@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`).
Using the old annotation would either not compile or produce deprecation warnings.

**What was used instead:**
```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@MockitoBean
private JobExecutionService jobExecutionService;
```

---

### ❌ Approach 3: Adding custom JPQL query methods directly in the repository

**What was considered:**
```java
// Considered but rejected
@Query("SELECT j FROM JobExecution j WHERE LOWER(j.jobName) LIKE %:name%")
Page<JobExecution> findByJobNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
```

**Why it was not used:**
- A separate query method would be needed for every filter combination (name only,
  status only, both, neither) — that is 4 methods or complex `@Query` conditions
- The `Specification` pattern handles all combinations with a single `findAll` call
- Hard-coded JPQL strings are harder to test in isolation

---

## 5. Approach That Worked

**JPA Specification pattern** with `JpaSpecificationExecutor`.

The solution follows a clean three-layer separation:

```
Controller  →  receives raw strings, builds Pageable
    ↓
Service     →  normalises inputs, parses enum, builds Specification
    ↓
Specification  →  builds SQL predicates from typed inputs
    ↓
Repository  →  executes findAll(Specification, Pageable), returns Page<Entity>
```

This is the Spring Data JPA recommended approach for dynamic queries and was confirmed
to work across all 19 tests.

---

## 6. File-by-File Code Walkthrough

### 6.1 `JobExecutionRepository.java`

```java
@Repository
public interface JobExecutionRepository
        extends JpaRepository<JobExecution, Long>,
                JpaSpecificationExecutor<JobExecution> {
}
```

**JpaRepository** provides:
- `save()`, `findById()`, `findAll()`, `deleteById()`, etc.

**JpaSpecificationExecutor** adds:
- `findAll(Specification<T> spec, Pageable pageable)` — used to apply dynamic filters
- `findAll(Specification<T> spec)` — without pagination
- `count(Specification<T> spec)` — count matching rows

No method body is needed. Spring Data JPA generates all implementations at startup via
JDK dynamic proxies.

---

### 6.2 `JobSpecification.java`

```java
public final class JobSpecification {

    private JobSpecification() {}   // utility class, never instantiated

    public static Specification<JobExecution> withFilters(String jobName, JobStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (jobName != null && !jobName.isBlank()) {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("jobName")),
                        "%" + jobName.trim().toLowerCase() + "%"
                    )
                );
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

**How the SQL translates:**

| Input | Generated SQL fragment |
|-------|----------------------|
| `jobName="payroll"` | `LOWER(job_name) LIKE '%payroll%'` |
| `status=COMPLETED` | `status = 'COMPLETED'` |
| both | `LOWER(job_name) LIKE '%payroll%' AND status = 'COMPLETED'` |
| neither | _(no WHERE clause — all rows)_ |

The `Specification<T>` interface is a `@FunctionalInterface`, so the lambda expression
`(root, query, cb) -> { ... }` is its implementation of the single abstract method
`toPredicate(Root<T>, CriteriaQuery<?>, CriteriaBuilder)`.

---

### 6.3 `BadRequestException.java`

```java
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

A thin wrapper that carries the error message. Being a `RuntimeException`, it does not
need to be declared in method signatures and propagates up the call stack until
`GlobalExceptionHandler` intercepts it.

---

### 6.4 `GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
```

**`@RestControllerAdvice`** — scans all `@RestController` classes and intercepts
uncaught exceptions globally.

**`@ExceptionHandler(BadRequestException.class)`** — this specific method runs only
when a `BadRequestException` is thrown anywhere in the request handling pipeline.

**`Map.of("error", ex.getMessage())`** — produces a consistent JSON structure:
```json
{ "error": "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED" }
```

---

### 6.5 `JobExecutionService.java` — new method

```java
public Page<JobExecutionResponse> getAllJobs(String jobName, String statusStr, Pageable pageable) {

    // Step A: normalise jobName
    String trimmedName = (jobName != null && !jobName.isBlank()) ? jobName.trim() : null;

    // Step B: parse and validate status
    JobStatus statusEnum = null;
    if (statusStr != null && !statusStr.isBlank()) {
        try {
            statusEnum = JobStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(JobStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid status value. Allowed values: " + allowed);
        }
    }

    // Step C: build specification and query
    Specification<JobExecution> spec = JobSpecification.withFilters(trimmedName, statusEnum);
    return jobExecutionRepository.findAll(spec, pageable).map(this::toResponse);
}
```

**Step A — normalise jobName:**
- `"payroll"` → `"payroll"` (passes through)
- `"  payroll  "` → `"payroll"` (trimmed)
- `"   "` or `""` → `null` (treated as no filter)
- `null` → `null` (no filter)

**Step B — parse status:**
- `"COMPLETED"` → `JobStatus.COMPLETED` (exact match)
- `"completed"` → `JobStatus.COMPLETED` (`toUpperCase()` handles case)
- `"FOOBAR"` → throws `BadRequestException`
- `null` or `""` → `null` (no status filter)

**Step C — query with specification:**
`jobExecutionRepository.findAll(spec, pageable)` returns a `Page<JobExecution>`.
`.map(this::toResponse)` converts each entity to a `JobExecutionResponse` DTO.
The resulting `Page<JobExecutionResponse>` carries `totalElements`, `totalPages`,
`content`, `number`, `size`, `first`, `last`, `empty` — all reflecting the
**filtered** dataset.

---

### 6.6 `JobController.java` — updated `getAllJobs`

```java
@GetMapping
public ResponseEntity<Page<JobExecutionResponse>> getAllJobs(
        @RequestParam(defaultValue = "0")        int page,
        @RequestParam(defaultValue = "10")       int size,
        @RequestParam(defaultValue = "startTime") String sortBy,
        @RequestParam(defaultValue = "desc")     String sortDir,
        @RequestParam(required = false)          String jobName,   // optional
        @RequestParam(required = false)          String status) {  // optional

    Sort sort = sortDir.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();

    PageRequest pageable = PageRequest.of(page, size, sort);
    return ResponseEntity.ok(jobExecutionService.getAllJobs(jobName, status, pageable));
}
```

`required = false` means the parameter is absent from the URL → Spring binds `null`
to the variable → the service treats `null` as "no filter".

---

## 7. Request Flow — End to End

```
Browser / Frontend
        │
        │  GET /api/jobs?jobName=payroll&status=COMPLETED&page=0&size=10
        ▼
JobController.getAllJobs()
   - Reads query params (jobName="payroll", status="COMPLETED")
   - Builds PageRequest(page=0, size=10, sort=startTime DESC)
   - Calls jobExecutionService.getAllJobs("payroll", "COMPLETED", pageable)
        │
        ▼
JobExecutionService.getAllJobs()
   - Trims jobName → "payroll"
   - Parses "COMPLETED" → JobStatus.COMPLETED
   - Calls JobSpecification.withFilters("payroll", COMPLETED)
   - Calls jobExecutionRepository.findAll(spec, pageable)
        │
        ▼
JobSpecification.withFilters()
   - Builds predicate: LOWER(job_name) LIKE '%payroll%'
   - Builds predicate: status = 'COMPLETED'
   - Returns AND combination of both predicates
        │
        ▼
JobExecutionRepository.findAll(spec, pageable)
   - JPA translates spec → SQL:
     SELECT * FROM job_execution
     WHERE LOWER(job_name) LIKE '%payroll%'
       AND status = 'COMPLETED'
     ORDER BY start_time DESC
     LIMIT 10 OFFSET 0
   - Returns Page<JobExecution> with filtered totalElements
        │
        ▼
JobExecutionService  (maps entity → DTO)
        │
        ▼
JobController  (wraps in ResponseEntity)
        │
        ▼
HTTP 200 OK
{
  "content": [ { "id": 1, "jobName": "Payroll Processing - January", ... } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true,
  "empty": false
}
```

---

## 8. API Reference

### `GET /api/jobs`

Returns a paginated list of job executions, optionally filtered.

**Query Parameters:**

| Parameter | Required | Default     | Description |
|-----------|----------|-------------|-------------|
| `page`    | No       | `0`         | Zero-based page index |
| `size`    | No       | `10`        | Records per page |
| `sortBy`  | No       | `startTime` | Field to sort by |
| `sortDir` | No       | `desc`      | Sort direction: `asc` or `desc` |
| `jobName` | No       | _(none)_    | Partial, case-insensitive name match |
| `status`  | No       | _(none)_    | Exact status: `RUNNING`, `COMPLETED`, or `FAILED` |

**Example Requests:**

```bash
# All jobs (no filters)
GET /api/jobs

# Search by name (case-insensitive)
GET /api/jobs?jobName=payroll

# Filter by status
GET /api/jobs?status=COMPLETED

# Both filters combined
GET /api/jobs?jobName=payroll&status=COMPLETED

# With pagination and sorting
GET /api/jobs?page=0&size=10&sortBy=startTime&sortDir=desc&jobName=payroll&status=COMPLETED
```

**Success Response (HTTP 200):**

```json
{
  "content": [
    {
      "id": 1,
      "jobName": "Payroll Processing - January",
      "status": "COMPLETED",
      "startTime": "2026-01-15T09:00:00",
      "endTime": "2026-01-15T09:30:00",
      "message": "Job completed successfully"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true,
  "empty": false
}
```

**Error Response (HTTP 400) — invalid status:**

```json
{
  "error": "Invalid status value. Allowed values: RUNNING, COMPLETED, FAILED"
}
```

---

## 9. Error Handling

### Invalid status value

**Trigger:** `?status=ANYTHING_NOT_IN_ENUM`

**Path:**
```
Controller receives "FOOBAR"
  → Service calls JobStatus.valueOf("FOOBAR")
  → Java throws IllegalArgumentException
  → Service catches it, throws BadRequestException("Invalid status value. Allowed values: ...")
  → GlobalExceptionHandler intercepts BadRequestException
  → Returns HTTP 400 with {"error": "..."}
```

The allowed values message is **always in sync** with the enum because it is built
dynamically:
```java
Arrays.stream(JobStatus.values()).map(Enum::name).collect(Collectors.joining(", "))
```
If a new status is added to `JobStatus` in the future, the error message updates
automatically.

---

## 10. Test Coverage

### Service unit tests — `JobExecutionServiceFilterTest` (11 tests)

These tests use **Mockito** to mock the repository. No database, no Spring context —
they run in milliseconds.

| Test Method | What It Verifies |
|---|---|
| `noFilters_returnsAll` | When both params are null, spec is built and all rows returned |
| `jobNameOnly_delegatesSpec` | `jobName="payroll"` filters correctly |
| `statusOnly_delegatesSpec` | `status="RUNNING"` filters correctly |
| `bothFilters_combineWithAnd` | Both filters applied together |
| `blankJobName_ignoredAsFilter` | `jobName="   "` (spaces only) is ignored |
| `emptyJobName_ignoredAsFilter` | `jobName=""` is ignored |
| `statusCaseInsensitive_accepted` | `status="completed"` (lowercase) is accepted |
| `invalidStatus_throwsBadRequest` | `status="INVALID_STATUS"` throws `BadRequestException` with all three enum values in the message |
| `pagination_reflectsFilteredDataset` | `totalElements`, `totalPages`, `size`, `number` are correct |
| `sorting_passedThroughToRepository` | Sort direction is preserved in the `Pageable` passed to the repository |
| `backwardCompatOverload_delegatesWithNullFilters` | `getAllJobs(Pageable)` still works |

### Controller slice tests — `JobControllerFilterTest` (7 tests)

These tests use `@WebMvcTest` to spin up only the Web MVC layer (no database,
no async processing). The service is replaced with a Mockito mock.

| Test Method | HTTP Scenario Tested |
|---|---|
| `noFilters_returns200WithAllJobs` | `GET /api/jobs` returns 200 with all items |
| `jobNameFilter_returns200WithFilteredJobs` | `GET /api/jobs?jobName=payroll` |
| `statusFilter_returns200WithFilteredJobs` | `GET /api/jobs?status=RUNNING` |
| `bothFilters_returns200` | `GET /api/jobs?jobName=payroll&status=COMPLETED` |
| `invalidStatus_returns400` | `GET /api/jobs?status=BOGUS` returns 400 with error body |
| `pageMetadata_presentInResponse` | All page fields present: `totalPages`, `number`, `size`, `first`, `last`, `empty` |
| `sortingParams_forwardedToService` | `sortBy` and `sortDir` params forwarded correctly |

### Test results

```
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 11. Key Concepts to Understand

### What is `Specification<T>`?

`Specification<T>` is a Spring Data JPA interface that wraps the JPA Criteria API.
It represents a single composable WHERE condition.

```java
@FunctionalInterface
public interface Specification<T> {
    Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb);
}
```

- `root` → the entity being queried (like a table alias in SQL)
- `query` → the overall SELECT statement object
- `cb` (CriteriaBuilder) → factory for building SQL expressions (`LIKE`, `=`, `AND`, etc.)

The lambda `(root, query, cb) -> { ... }` is essentially writing SQL conditions in Java.

---

### Why not use a `@Query` annotation instead?

| `@Query` annotation | `Specification` |
|---|---|
| Fixed SQL at compile time | Dynamic SQL at runtime |
| Need one method per filter combination | One method handles all combinations |
| Hard to reuse predicates | Predicates are composable objects |
| Harder to unit test isolation | Specification logic can be tested independently |

For **optional** filters that can appear in any combination, Specifications are the
superior choice.

---

### What does `JpaSpecificationExecutor` provide?

Adding `JpaSpecificationExecutor<JobExecution>` to the repository interface unlocks
these methods (all auto-implemented by Spring Data JPA):

```java
Optional<T>  findOne(Specification<T> spec)
List<T>      findAll(Specification<T> spec)
Page<T>      findAll(Specification<T> spec, Pageable pageable)  ← used here
List<T>      findAll(Specification<T> spec, Sort sort)
long         count(Specification<T> spec)
boolean      exists(Specification<T> spec)
```

---

### How does `@RestControllerAdvice` work?

```
@RestControllerAdvice
└── intercepts all uncaught exceptions from @RestController classes
    └── routes exception to the method annotated with @ExceptionHandler(ExceptionType.class)
        └── returns the ResponseEntity produced by that method as the HTTP response
```

Without this, an unhandled `BadRequestException` would produce an opaque HTTP 500
response. With it, the client receives a clean HTTP 400 with a descriptive message.

---

### Spring Boot 4 test annotation changes

| Annotation | Spring Boot 2/3 package | Spring Boot 4 package |
|---|---|---|
| `@WebMvcTest` | `o.s.boot.test.autoconfigure.web.servlet` | `o.s.boot.webmvc.test.autoconfigure` |
| `@MockBean` | `o.s.boot.test.mock.mockito` | Replaced by `@MockitoBean` (`o.s.test.context.bean.override.mockito`) |

These changes are breaking and require updating imports in test files when upgrading to
Spring Boot 4.
