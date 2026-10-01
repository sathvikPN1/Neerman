# Nirmaan Reimbursement Platform

CS5013 *Programming with AI* course project.

Nirmaan, the pre-incubator at IIT Madras, funds student startup teams. Teams spend their own money on approved expenses and then claim reimbursement. Today this runs entirely over email. This platform replaces that process:

- teams submit claims with their invoices and payment proofs;
- Nirmaan staff verify the claims;
- the COO approves them;
- Finance pays them;
- every step is tracked in an audit log.

> **Status: work in progress.** This first commit contains the project skeleton and the database schema. Application features are added in later commits.

## Tech stack

Java, Spring Boot 3.5 (Web MVC, Security, Data JPA, Validation, Mail), Thymeleaf + HTMX, PostgreSQL 16 with Flyway migrations, MinIO (S3-compatible storage), MailHog for development email, Docker Compose.

## What is in this commit

| Path | Purpose |
|---|---|
| `pom.xml` | Maven build and dependencies |
| `docker-compose.yml`, `Dockerfile` | Local stack: PostgreSQL, MinIO, MailHog, app |
| `src/main/resources/application.yml` | Configuration. All secrets come from environment variables. |
| `src/main/resources/db/migration/V1__init.sql` | Database schema |
| `src/main/resources/db/migration/V2__reference_data.sql` | Permissions, default role bundles, expense categories, default settings |

### Schema highlights

- Money is stored as `NUMERIC(12,2)` in INR. Timestamps are `TIMESTAMPTZ` (stored in UTC, shown in IST).
- The claim status is restricted by a `CHECK` constraint to `DRAFT`, `SUBMITTED`, `VERIFIED`, `RETURNED`, `APPROVED`, `PAID` and `REJECTED`.
- `audit_log` is append-only: a trigger rejects every `UPDATE` and `DELETE`.
- Permissions are fine-grained. `STAFF_MANAGE` belongs to the COO only, and the database refuses to grant it to anyone.
- Expense categories come from the Nirmaan Student Guide: allowed, needs pre-approval, or not allowed.

## Running what exists so far

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Flyway creates the schema on start-up. Check it with:

```bash
curl http://localhost:8080/actuator/health
```

## AI usage

This project is built with the help of an AI coding assistant (Claude Code). We have reviewed every commit and can explain any part of the code.
