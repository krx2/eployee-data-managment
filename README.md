# Employee Data Service

A small backend microservice for storing and managing employee records for an internal HR system, with particular attention to how the social security number (SSN) is protected at rest and in transit.

## Tech stack

- Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation)
- PostgreSQL 16 and the application itself, both via Docker Compose (multi-stage `Dockerfile`)
- Flyway (versioned schema migrations)
- AES-256-GCM (SSN encryption at rest)
- JUnit 5, Mockito, AssertJ, Testcontainers

## Running locally

The whole service — app and database — is containerized. **No local JDK is required** to run it; you only need Docker.

### Prerequisites

- Docker Desktop
- JDK 25 — only if you want to build/run/test outside Docker (e.g. from an IDE)

### Option 1 — fully containerized (recommended)

1. Provide the required secrets. Create a `.env` file in the project root (this file is gitignored, never committed):

   ```bash
   {
     echo "APP_ENCRYPTION_KEY=$(openssl rand -base64 32)"
     echo "APP_SSN_LOOKUP_PEPPER=$(openssl rand -base64 32)"
     echo "APP_API_KEY=$(openssl rand -hex 16)"
   } > .env
   ```

   On Windows PowerShell:

   ```powershell
   $key = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   $pepper = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   $apiKey = -join ((1..32) | ForEach-Object { "{0:x}" -f (Get-Random -Maximum 16) })
   @"
   APP_ENCRYPTION_KEY=$key
   APP_SSN_LOOKUP_PEPPER=$pepper
   APP_API_KEY=$apiKey
   "@ | Out-File -Encoding utf8 .env
   ```

   Docker Compose reads `.env` automatically; the app container fails fast with a clear error if any of the three variables isn't set — there is no insecure default. See [Security](#security) below for what each one guards.

2. Build and start everything:

   ```bash
   docker compose up --build -d
   ```

   This builds the app image (multi-stage `Dockerfile`, JDK 25 for the build stage, slim JRE 25 for the runtime stage), starts `postgres:16-alpine`, waits for it to report healthy, then starts the app container. Flyway migrates the schema on startup.

3. The API is available at `http://localhost:8080` on the host, exactly as if it were running locally.

4. Tear down: `docker compose down` (add `-v` to also drop the database volume).

### Option 2 — run the app locally against a containerized database

Useful when you want to debug/step through the app in an IDE.

```bash
docker compose up -d postgres
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
export APP_SSN_LOOKUP_PEPPER=$(openssl rand -base64 32)
export APP_API_KEY=$(openssl rand -hex 16)
./mvnw spring-boot:run
```

On Windows PowerShell, generate the values the same way as above and set them with `$env:NAME = "..."`. If running from an IDE run configuration, set all three as environment variables there instead.

### Running the tests

```bash
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
./mvnw test
```

Only `APP_ENCRYPTION_KEY` needs to be a real, properly-random value for tests — the crypto round-trip tests actually exercise it. `app.api-key` and `app.ssn-lookup-pepper` are fixed to constant test values via `@TestPropertySource` in the test classes that need them, so `APP_API_KEY`/`APP_SSN_LOOKUP_PEPPER` don't need to be exported for `./mvnw test` to pass.

This needs JDK 25 and Docker (not just the `postgres` container from Option 2, but Docker itself): most tests need a running Postgres — either the one from `docker compose up -d postgres`, or the integration test's **own** disposable container, which it starts automatically via Testcontainers.

## API

Every endpoint requires an `X-API-Key` header (see [Security](#security)) except the OpenAPI/Swagger paths.

| Method | Path              | Description                          |
|--------|-------------------|--------------------------------------|
| POST   | `/employees`       | Create a new employee record         |
| GET    | `/employees/{id}`   | Retrieve a single employee            |
| GET    | `/employees`        | List employees (paginated)            |
| DELETE | `/employees/{id}`   | Delete an employee record             |

Interactive docs (no API key needed): `http://localhost:8080/swagger-ui/index.html`.

Example:

```bash
curl -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $APP_API_KEY" \
  -d '{"firstName":"Jan","lastName":"Kowalski","dateOfBirth":"1990-01-01","gender":"MALE","socialSecurityNumber":"123-45-6789"}'
```

```json
{
  "id": "a92894c9-ba11-4bb0-a1aa-0dab9216ba13",
  "firstName": "Jan",
  "lastName": "Kowalski",
  "dateOfBirth": "1990-01-01",
  "gender": "MALE",
  "maskedSsn": "***-**-6789"
}
```

The full SSN is never present in any response — only `maskedSsn` (last 4 digits) is returned.

Errors follow [RFC 7807 `ProblemDetail`](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Validation failed",
  "errors": ["socialSecurityNumber: must be a valid SSN in format XXX-XX-XXXX"]
}
```

Creating an employee with an SSN that already exists returns `409 Conflict`. Missing/wrong `X-API-Key` returns `401 Unauthorized`.

## Security

Three secrets are required at startup, none defaulted, none committed — a missing one is a hard startup failure rather than an insecure fallback:

| Env var | Used for |
|---|---|
| `APP_ENCRYPTION_KEY` | AES-256-GCM key encrypting the SSN at rest (reversible, see [Technology choices](#technology-choices-and-why)) |
| `APP_SSN_LOOKUP_PEPPER` | HMAC-SHA256 secret for the deterministic `ssn_lookup_hash` column, used only to detect duplicate SSNs (never to recover the value) |
| `APP_API_KEY` | Shared secret every request must present via the `X-API-Key` header |

**Authentication is intentionally minimal.** A servlet filter (`ApiKeyAuthFilter`) checks a single shared secret via `MessageDigest.isEqual` (constant-time — a plain `String.equals` would leak timing information about how much of the key matched) — enough to close "every endpoint is wide open" for this exercise, but not what a real deployment should use. In production this would be OAuth2 client-credentials between internal services, or mTLS at the service-mesh level, not an application-level shared secret. The 401 response is a real `ProblemDetail`, serialized through the same `ObjectMapper` bean the rest of the app uses — the filter runs before `DispatcherServlet` so `@RestControllerAdvice` can't catch it, but that's no reason to hand-rebuild the error shape as a string literal.

**SSN validation is semantic, not just syntactic.** Beyond the `XXX-XX-XXXX` shape, `@ValidSsn` enforces the SSA's structural rules: area ≠ `000`/`666`/`900`–`999`, group ≠ `00`, serial ≠ `0000`.

**Duplicate SSNs are rejected**, not silently accepted. Every SSN is also stored as a deterministic HMAC (`ssn_lookup_hash`, unique-indexed) alongside the AES-GCM ciphertext used for the actual value — the hash is only ever used for the uniqueness check, never to recover the SSN itself. `EmployeeService.create()` catches the resulting `DataIntegrityViolationException`, checks the violated constraint's *name* (not just "some integrity violation happened"), and only then throws a dedicated `DuplicateSsnException` → `409 Conflict`; any other, unrelated integrity violation still surfaces as a 500 instead of being misreported as a duplicate SSN.

**The decrypted SSN never leaves the entity.** `Employee` exposes it only through a package-private `ssnForInternalUseOnly()`, callable solely by code in the same package (`EmployeeMapper`) — exposing it elsewhere requires a deliberate visibility change, not just an extra line of code.

**Validation messages are pinned to English via a scoped bean, not a global JVM setting.** An earlier version of this fix called `Locale.setDefault(Locale.ENGLISH)` in a static initializer, which — while it worked — silently changed the default locale for the *entire JVM*, not just Bean Validation. It's now a dedicated `LocalValidatorFactoryBean` with a `MessageInterpolator` that always resolves messages in English, regardless of the host's locale, without touching anything else in the process.

## Technology choices and why

**PostgreSQL over a NoSQL store.** The data is small, fixed-shape, and relational in nature (a single well-defined `Employee` record) — there's no variable schema or document-nesting need that would justify a document store. PostgreSQL also gave a straightforward path to Testcontainers-based integration testing.

**Flyway over `hibernate.ddl-auto=update`.** A versioned migration (`V1__create_employee_table.sql`) is the schema's single source of truth; Hibernate is set to `ddl-auto=validate` so it only checks the entity mapping against what Flyway created, it never mutates the schema itself. This is a small amount of extra ceremony for a take-home exercise, but it's the difference between "the schema is whatever Hibernate inferred" and "the schema is an auditable, reviewable artifact" — the latter is how I'd want a real HR system's schema managed.

**AES-256-GCM encryption over one-way hashing for the SSN.** Both were explicitly allowed by the assignment; the choice comes down to a real tradeoff:

- *Hashing* (e.g. BCrypt/Argon2) is a good fit when you only ever need to *check* a value against a stored form (like a password) and never need it back. It's a poor fit for a 9-digit SSN: the input space is small enough (well under 10^9, and further constrained by real allocation rules) that even a slow, salted hash is within reach of an offline guessing attack if the database ever leaks. It's also strictly one-way — there's no legitimate way to later produce a masked view, or supply the real number to a downstream system (payroll, tax reporting) that legitimately needs it.
- *Encryption* (AES-256-GCM here) is reversible under a key that's kept outside the database, which is exactly the property needed for occasional legitimate re-use of the value, while the value at rest is still ciphertext an attacker can't recover without the key. GCM specifically gives authenticated encryption (tamper-evidence) and a fresh random nonce per row means two employees with the same SSN produce different ciphertext — no accidental leakage of duplicates.

The key is read from an environment variable (`APP_ENCRYPTION_KEY`) and never committed or defaulted; missing it is a hard startup failure rather than a silent fallback to something insecure. A `key_version` column is already in the schema so key rotation is possible later without a data migration (see "What I'd do differently" below — rotation itself isn't implemented yet).

**UUID primary keys over auto-increment.** Sequential integer IDs leak information (roughly how many employees exist, and the order they were created) through a public-facing API. UUIDs generated by Hibernate application-side avoid that at negligible cost for this data volume.

**`ProblemDetail` (RFC 7807) over a hand-rolled error DTO.** Spring 6/Boot 3+ ships this as a first-class return type from `@ExceptionHandler` methods, so it's less code than a custom error shape and it's a recognized standard rather than another bespoke JSON format for API consumers to learn.

**Package-by-feature-ish layering.** `employee` (entity, repository, service, controller, DTOs), `crypto` (encryption service, JPA converter, masking), `common` (cross-cutting exception handling). For a single-entity service this is a light structure, not a strict hexagonal/clean-architecture split — that felt like unnecessary ceremony at this scale, but the crypto concern is still cleanly isolated from the employee domain code.

## What I'd do differently with more time

This service went through two review passes (see git history / `IMPLEMENTATION_PLAN.md`): the first fixed a real bug (some malformed-input cases were returning 500 instead of 400) and closed several small gaps; the second added minimal authentication, semantic SSN validation, stricter encapsulation of the decrypted SSN, duplicate-SSN detection, OpenAPI docs, a `DELETE` endpoint, locale-independent validation messages, and full containerization. What's below is what's still consciously left out, roughly in the order I'd tackle it:

- **Key rotation.** The `key_version` column exists but nothing reads or acts on it yet. Doing this properly runs into a real architectural constraint: a JPA `AttributeConverter` only ever sees the one column it's attached to, not sibling columns on the same row — so it can't itself consult `key_version` to pick a key. The correct minimal fix is to make the ciphertext self-describing (prefix the key version inside the encoded blob itself, alongside the nonce), and either repurpose or drop the `key_version` column in a follow-up migration once that lands.
- **Real authentication.** The current `X-API-Key` filter is a single shared secret — enough to close "every endpoint is wide open," but production would need OAuth2 client-credentials or mTLS, not an application-level static key.
- **Audit logging.** Nothing today records who created, read, or deleted a given employee record, which is a standard compliance expectation for a system holding SSNs. Meaningful "who" logging is more valuable once authentication carries a real principal rather than a shared secret; in the meantime the access itself (action, employee id, timestamp) could already be logged through a dedicated logger so it's routable to a separate audit sink later without revisiting the call sites.
- **Retention policy.** `DELETE /employees/{id}` exists now (hard delete), but there's still no actual retention policy, which a real HR system holding SSNs would need to reconcile against something like GDPR's right to erasure — and those two concerns can conflict (e.g. legally mandated payroll retention periods). That's a real policy decision, not just an endpoint.
- **CI** (GitHub Actions) running `mvn test` on every push — the Testcontainers-based integration test is exactly the kind of thing that should run automatically, not just locally.
- **`@ConfigurationProperties`** instead of raw `@Value` fields for the three secrets, mostly for testability/override convenience in more complex configurations.
- **Confirming the repository is actually public on GitHub** before treating the assignment's "public GitHub repository" deliverable as satisfied — worth a final check before submission, not something a local working tree can confirm on its own.
- **Hardening the app-level container for production** — no non-root user, no distroless base, no image scanning today.

What's **not** on this list on purpose, because it's a reasonable simplification for the scope of a few-hours exercise rather than an oversight: no uniqueness constraint on name+date-of-birth combinations (two employees with different SSNs but identical names/DOB are fine — only exact SSN duplicates are rejected).

## AI tool usage

I used **Claude Code** (Anthropic's CLI agent) throughout, working stage-by-stage from an implementation plan we wrote together up front (data model, SSN encryption, REST API, persistence, tests), rather than asking for the whole service in one shot. Each stage was actually compiled and exercised against a real, running PostgreSQL container before moving on — not just "does it compile," but real `curl` calls against the running app and real rows inspected in `psql`.

That verification step mattered in practice: this project pins **Spring Boot 4.1**, which turned out to have quietly modularized several things a "Spring Boot 2.x/3.x textbook" approach gets wrong. The AI's first pass repeatedly reached for the familiar shape and was wrong in specific, checkable ways:

- `flyway-core` alone compiles fine but silently does nothing at runtime — the actual Spring autoconfiguration that wires up and runs Flyway moved to a separate `spring-boot-starter-flyway` module. This was only caught because the migration logs were conspicuously absent from a real app run, not because anything failed to compile.
- Testcontainers 2.x renamed its module artifacts (`org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`) and made `PostgreSQLContainer` non-generic, breaking the "textbook" `PostgreSQLContainer<>` usage.
- `@MockBean` no longer exists in this Spring generation — the AI's first draft of the controller slice test used it, and it doesn't compile; the working replacement is `@MockitoBean` from a different package.
- Jackson's `ObjectMapper` moved to a `tools.jackson.databind` package in this Spring Boot generation, not the familiar `com.fasterxml.jackson.databind` one.

I rejected/corrected each of these rather than accepting the first plausible-looking code, specifically by actually running things (compiling, executing tests, hitting live endpoints) rather than trusting that syntactically reasonable Spring Boot code would behave the way an older mental model of Spring Boot suggests it should. That's the concrete "changed or rejected an AI suggestion" example for this exercise: not a design disagreement, but catching several instances where the generated code was outdated for the pinned framework version and only verification against a real running instance exposed it.

The same discipline carried through two later review passes: after getting a security/completeness review back (the kind an interviewer would give), each fix — the 500-vs-400 bug, minimal API-key auth, semantic SSN validation, duplicate detection, containerizing the app itself — was verified against a real running instance (container or host) before being called done, including deliberately reproducing bugs first (e.g. confirming the 500s, and confirming Polish validation messages on the host's `pl_PL` locale) so the "fix" claim wasn't just asserted.
