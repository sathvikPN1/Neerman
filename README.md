# Nirmaan Reimbursement Platform

Nirmaan is the pre-incubator at IIT Madras. This platform replaces its email-based reimbursement process:

- Student teams submit claims with their invoices and payment proofs.
- Nirmaan staff verify the claims.
- The COO approves them from a mobile-first screen.
- Finance records the payments.

Every step is tracked, audit-logged and visible to the people involved.

**Status:** Phase 1 (foundation and MVP) is complete. See [ASSUMPTIONS.md](ASSUMPTIONS.md) for decisions made where the brief was open.

## Quick start

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| App | http://localhost:8080 |
| MailHog (all outgoing email) | http://localhost:8025 |
| MinIO console (uploaded documents) | http://localhost:9001 (`nirmaan` / `nirmaan-secret`) |

If a port is already in use, override it, for example: `APP_PORT=8091 APP_BASE_URL=http://localhost:8091 docker compose up --build`. You can also override `PG_PORT`, `MINIO_PORT`, `MINIO_CONSOLE_PORT`, `MAILHOG_SMTP_PORT` and `MAILHOG_UI_PORT`.

### Demo logins

The password for every demo account is `Demo@1234`.

| Role | Email | Lands on |
|---|---|---|
| COO (all permissions) | `coo@nirmaan.local` | Approvals |
| Nirmaan staff | `staff@nirmaan.local`, `staff2@nirmaan.local` | Verification queue |
| Finance | `finance@nirmaan.local` | Payment queue |
| Team member, AgriSense (Pratham) | `priya@agrisense.local`, `arjun@agrisense.local` | Team dashboard |
| Team member, MedTrack (Pratham) | `meera@medtrack.local` | Team dashboard |
| Team member, VoltCycle (Akshar) | `rahul@voltcycle.local` | Team dashboard |

The demo data includes one claim at each stage:

- AgriSense: one claim waiting for verification, plus a draft.
- MedTrack: one verified **priority** claim waiting for the COO.
- VoltCycle: one approved claim waiting for Finance.

### Try the full flow

1. **Team.** Sign in as `priya@agrisense.local` and create a **New claim**.
   - Choose "Accommodation" to see the blocking warning.
   - Enter an amount above the remaining budget to see the over-budget note.
   - Save, upload an invoice and a payment proof, then **Submit**.
2. **Staff.** Sign in as `staff2@nirmaan.local`, open the claim from the **Verification queue**, review the documents side by side, then **Verify**.
3. **COO.** Sign in as `coo@nirmaan.local`. On **Approvals**, the priority claim is at the top. Tap a row to expand it, then:
   - **Approve**, approve partially with a reason, or select several rows and **Approve selected**.
4. **Finance.** Sign in as `finance@nirmaan.local`, enter the payment date, mode and UTR, then **Mark as paid**.
5. Back as the team, you'll see the status timeline, the comment thread and notifications. The emails are in MailHog.

To see the four-eyes rule: staff who verified a claim cannot approve it, even with `CLAIM_APPROVE`. Only the COO can do both.

### Run locally without Docker for the app

Requires a JDK matching `<java.version>` in `pom.xml` and Maven 3.9+.

```bash
docker compose up -d postgres mailhog
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile bootstraps `coo@nirmaan.local` / `Demo@1234`, seeds demo data, logs emails to the console and stores uploads under `./data/uploads`.

### Tests

```bash
mvn test
```

Docker must be running: integration tests start PostgreSQL 16 with Testcontainers. The suite has 182 tests, and service-layer line coverage is about 93% (JaCoCo report at `target/site/jacoco/index.html`). It covers:

- every legal and illegal state transition;
- the permission matrix for each protected endpoint and service method;
- team data isolation;
- COO invariants and hand-over;
- grant expiry;
- the four-eyes rule;
- concurrent approvals;
- budget limits;
- audit-log immutability;
- the full submit → verify → approve → pay flow over HTTP.

## Configuration

All secrets come from environment variables.

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/nirmaan`, `nirmaan`, `nirmaan` |
| `BOOTSTRAP_COO_EMAIL`, `BOOTSTRAP_COO_PASSWORD`, `BOOTSTRAP_COO_NAME` | Creates the first COO on start-up, only if no COO exists | (none) |
| `DEMO_DATA` | Seeds demo users, teams and claims | `false` (`true` in compose) |
| `APP_BASE_URL` | Used in email links | `http://localhost:8080` |
| `MAIL_MODE` | `log` (console) or `smtp` | `log` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP settings | MailHog |
| `STORAGE_TYPE` | `local` or `s3` | `local` |
| `STORAGE_LOCAL_ROOT` | Upload folder for `local` | `./data/uploads` |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` | S3/MinIO settings | MinIO in compose |
| `SPRING_PROFILES_ACTIVE=prod` | JSON structured logs | (none) |

For a real deployment, set strong `BOOTSTRAP_COO_*` and storage credentials, set `DEMO_DATA=false`, and put the app behind HTTPS.

## Architecture

Server-rendered Spring MVC with Thymeleaf and HTMX, Tailwind from the CDN, PostgreSQL 16 with Flyway, and Spring Security.

The code is packaged by feature:

```
com.nirmaan.reimburse
 ├─ config/        security, per-request authority refresh, login rate limit, storage, mail, clock
 ├─ common/        audit (append-only log), money (₹ Indian format), time (IST), exceptions,
 │                 storage (StorageService: local disk / S3), security (principal, AccessPolicy), web helpers
 ├─ user/          Permission enum, grants with expiry, role default bundles, invites, COO bootstrap & hand-over
 ├─ cohort/ team/ category/ settings/
 ├─ claim/         Claim entity, ClaimStateMachine (the only place status changes), queues, priority,
 │                 comments, bulk decisions, notifier
 ├─ document/      uploads (magic-byte type check, SHA-256, 10 MB cap), authorised download
 ├─ payment/       mark paid (UTR required)
 ├─ notification/  in-app notifications + EmailSender (log / SMTP)
 ├─ demo/          demo data seeder (runs through the real services)
 └─ web/           thin controllers
```

### Key design points

**Permissions, not roles.**
- Every protected service method carries `@PreAuthorize("hasAuthority('…')")`.
- A role is only a default bundle of permissions; the COO edits each user's grants, optionally with an expiry.
- The COO implicitly holds every permission.
- `STAFF_MANAGE` cannot be granted. This is enforced in the service and by a database `CHECK` constraint.

**Permission changes apply on the next request.**
- `AuthorityRefreshFilter` rebuilds the principal from the database on every request.
- Grants, revocations, expiries, hand-over and deactivation take effect on the user's next click, without re-login.

**Data scoping lives in services.**
- `AccessPolicy` ensures team members only ever see their own team, whatever URL they call.
- Integration tests prove this both at the HTTP layer and at the service layer.

**One state machine.**
- `ClaimStateMachine.transition()` is the only place a claim's status changes. It validates:
  - the source state;
  - who may act;
  - required reasons;
  - documents, category and budget rules;
  - the four-eyes rule;
  - the optimistic-lock version.
- It writes one audit entry per transition: actor, permission used, on-behalf-of, from → to, comment.

**Audit log is append-only.** A PostgreSQL trigger rejects `UPDATE` and `DELETE` on `audit_log`.

**Concurrency.**
- `@Version` on claims, with the version echoed in every form, prevents double action on the same claim.
- A row lock on the team serialises approvals so the budget cannot be overspent.

**Uploads.**
- The file type is detected from its content (PDF, JPEG, PNG, HEIC), not its name.
- Files are stored under random keys behind `StorageService`.
- They are served only through `/documents/{id}`, after an access check, with `nosniff` and `no-store` headers.

**Pluggable authentication.**
- Form login uses BCrypt.
- `PrincipalLoader.loadByEmail()` is the seam for adding IITM SSO / OAuth2 (`http.oauth2Login()`): map the external identity's email to a principal.

**Timezones.** Timestamps are stored as `TIMESTAMPTZ` in UTC and displayed in Asia/Kolkata as `dd MMM yyyy`. Money is `NUMERIC(12,2)` and displayed as `₹1,25,000.00`.

## Common admin tasks

**Add or change an expense category.**
- Anyone with `CATEGORY_MANAGE` (the COO by default) can use **Categories** in the top menu.
- Expand a category to edit it, or use "Add a category" at the bottom.
- Flags: reimbursable, requires pre-approval, pre-approval recommended, detailed justification, active.
- Deactivating a category hides it from new claims and keeps history intact.

**Change a team's budget or program.**
- Go to **Teams**, choose the team, then **Edit team & budget** (requires `TEAM_MANAGE`).
- Leave the budget empty to use the program default.
- Every change is audit-logged.

**Change the default budgets** (Pratham ₹2,00,000, Akshar ₹5,00,000).
- They live in the `settings` table under `budget.default.pratham` and `budget.default.akshar`.
- A settings screen arrives in Phase 3. Until then:

  ```sql
  update settings set value = '250000.00' where key = 'budget.default.pratham';
  ```

**Add staff or change permissions.**
- Go to **Staff & permissions** (COO only).
- Invite a person by email, then tick their permissions. Each permission can have an optional last day.
- From the same page you can deactivate a user or transfer the COO role.

**Add a new permission.**
1. Add it to the `Permission` enum.
2. Add a Flyway migration inserting it into `permissions`.

Start-up fails if the two drift apart.

## Roadmap

- **Phase 2.** Pre-approvals, invoice checklist with PDFBox extraction and pre-fill, duplicate detection, encrypted bank details, finance batch export.
- **Phase 3.** Daily digest, signed one-tap email approvals, auto-approval threshold, delegation UI, SLA reminders, Pratham → Akshar promotion.
- **Phase 4.** Analytics, Excel reports, global search, OCR hook.
