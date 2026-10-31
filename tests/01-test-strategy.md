# 1. Test Strategy

## 1.1 System under test

Server-rendered Spring MVC + Thymeleaf + HTMX application, PostgreSQL 16 (Flyway), Spring Security (form login, BCrypt, CSRF), local-disk or S3 document storage, email through SMTP or log.

Four roles: **COO**, **Nirmaan staff**, **Finance**, **Team member**. Authorisation is permission-based (15 permissions, `STAFF_MANAGE` COO-only).

Claim life cycle:

```
DRAFT ──submit──▶ SUBMITTED ──verify──▶ VERIFIED ──approve──▶ APPROVED ──mark paid──▶ PAID
  ▲                  │  │                 │  │                   │
  │                  │  └──return/reject──┘  └──return/reject    └──reopen (COO)──▶ SUBMITTED
RETURNED ◀───return──┘                                REJECTED ──reopen (COO)──▶ SUBMITTED
  └──resubmit──▶ SUBMITTED         SUBMITTED ──COO approve (verify+approve)──▶ APPROVED
```

## 1.2 Scope

**In scope (Phase 1):** login and session, invitations, staff and permissions, COO hand-over, cohorts, teams, members, budgets, categories, claims, documents, workflow, priority, COO approvals (single and bulk), payments, comments, notifications, audit log, team dashboard.

**Out of scope (later phases):** pre-approvals, PDF extraction, duplicate detection, bank details, batch export, daily digest, one-tap email approval, analytics, OCR. Their absence is tested only where Phase 1 enforces a rule (for example, Travel claims cannot be submitted).

## 1.3 Test levels

| Level | What | Where | Tooling |
|---|---|---|---|
| Unit | Pure logic: money, time, file type detection, warnings, queue ordering, budget snapshot, login limiter, permission service | `src/test/java/.../claim`, `common`, `config`, `document`, `team`, `user` | JUnit 5, AssertJ, Mockito |
| Integration (service) | Services against real PostgreSQL: workflow, budgets, concurrency, audit trigger | `src/test/java/.../it` (`*IT`) | Spring Boot Test, Testcontainers |
| Integration (HTTP) | Controllers, security filters, CSRF, redirects | `it/AccessControlIT`, `HappyPathIT`, `PermissionMatrixIT` | MockMvc |
| System / E2E (manual) | Full flows in a browser with MailHog and MinIO | [02](02-black-box-test-cases.md) | Browser, MailHog |
| Security | AuthN/AuthZ, isolation, upload, CSRF, session | [04](04-security-tests.md) | MockMvc, manual, OWASP ZAP (optional) |
| Non-functional | Concurrency, performance, usability, accessibility, compatibility | [05](05-non-functional-tests.md) | JUnit threads, k6/JMeter (optional), Lighthouse |

## 1.4 Test design techniques

| Technique | Used for |
|---|---|
| Equivalence partitioning | Form fields, roles, categories, file types |
| Boundary value analysis | Amount (₹1.00, 10 digits), justification (30 / 2000), UTR (40), comment (4000), file (10 MB), login lock (5 failures, 15 min), expense date (today / tomorrow), grant expiry (last day, inclusive) |
| Decision tables | Who can do what at which state (permissions × status), approval rules |
| State transition testing | Every legal and illegal (status, action) pair: 7 actions × 7 statuses |
| Use-case / scenario testing | End-to-end flows per role |
| Error guessing | Double clicks, stale forms, wrong MIME, renamed files, URL tampering |
| Statement / branch / condition coverage | White-box cases in [03](03-white-box-test-cases.md) |
| Mutation-style thinking | Each business rule has a test that fails if the rule is removed |

## 1.5 Environments

| Env | Purpose | Setup |
|---|---|---|
| CI / developer | Automated tests | `mvn test`, Docker for Testcontainers |
| Local compose | Manual and exploratory testing | `docker compose up --build`, `DEMO_DATA=true` |
| Staging (recommended) | UAT, security scan, performance | Same image, `DEMO_DATA=false`, HTTPS, real SMTP/S3 |

Time-dependent tests (grant expiry, rate limit, future dates) use an injectable `Clock`. Do not rely on wall-clock sleeps.

## 1.6 Entry and exit criteria

**Entry:** build compiles; migrations apply to an empty database; `/actuator/health` is UP.

**Exit:**
- 100% of the P1 cases pass; at least 95% of P2.
- No open Critical or High defect. Medium defects have an agreed workaround.
- Line coverage of the service layer stays at or above 90% (baseline 93%).
- Every legal and illegal state transition has a test.
- Every protected endpoint appears in the permission matrix test.

## 1.7 Priorities

- **P1:** money, permissions, data isolation, state machine, audit. A defect here can lose money or leak data.
- **P2:** validation, notifications, queues, usability of main flows.
- **P3:** cosmetics, secondary screens, edge formats.

## 1.8 Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Two approvers act on the same claim | Double decision | Optimistic lock (`@Version`); test TC-WF-040 / NF-CON-001 |
| Concurrent approvals overspend a budget | Financial loss | Team row lock; test NF-CON-002 |
| Team member reads another team's claim or document | Data leak | `AccessPolicy` in services; TC-SEC-020–024 |
| Permission change not applied until re-login | Stale access | `AuthorityRefreshFilter`; TC-USR-020–024 |
| Disguised upload (script renamed `.pdf`) | Stored XSS / malware | Magic-byte detection, `nosniff`; TC-DOC-010–016 |
| In-memory rate limiter lost on restart or in a cluster | Brute force | Documented assumption; NF-REC-003 |
| Email failure hides a notification | Missed action | In-app notification is the source of truth; TC-NTF-010 |
| Audit log edited | Compliance | DB trigger; TC-AUD-004 |
| Java version drift (pom is on 25, brief says 21) | Build break | Build in CI on the pom's version |

## 1.9 Roles and responsibilities

| Activity | Owner |
|---|---|
| Unit and integration tests | Developer who changes the code |
| Manual black-box and exploratory | QA / product owner |
| Security review | QA with a second reviewer |
| UAT | One person per role: COO, staff, finance, team member |

## 1.10 Defect handling

Severity: **Critical** (money, security, data loss), **High** (a main flow is blocked), **Medium** (workaround exists), **Low** (cosmetic). Use the template in [08](08-execution-and-defect-templates.md).
