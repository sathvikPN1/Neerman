# 5. Non-Functional Test Cases

Expected volumes (from ASSUMPTIONS #26): tens of claims per queue, tens of teams, a handful of staff. Targets below are proposals; confirm them with the product owner.

## 5.1 Concurrency and data integrity (CON)

| ID | P | Scenario | Method | Expected | Auto |
|---|---|---|---|---|---|
| NF-CON-001 | 1 | Two approvers act on the same claim at the same time | 2 threads, same `expectedVersion` | One succeeds; the other gets `ConcurrentUpdateException`; one audit entry | PlatformIT `twoApproversCannotDoubleAct…` |
| NF-CON-002 | 1 | Two approvals for two claims of one team that together exceed the budget | 2 threads, budget fits only one | Exactly one succeeds (team row lock); the budget is never negative | PlatformIT `approvalsCannotOverspend…` |
| NF-CON-003 | 1 | 20 parallel approvals across 5 teams | Thread pool | No deadlock; each team within budget; all audit entries present | Gap |
| NF-CON-004 | 1 | Two finance users pay the same claim | 2 threads | One payment row | Gap |
| NF-CON-005 | 2 | Bulk approval while another user approves one of the rows | Interleave | The bulk result reports that one as failed; the rest are fine | Gap |
| NF-CON-006 | 2 | Submit while the team edits | Parallel | Consistent state, no half-saved data | Gap |
| NF-CON-007 | 2 | Parallel claim creation | 50 threads | 50 unique codes, no gaps beyond the sequence's | Gap |
| NF-CON-008 | 2 | Parallel uploads to one claim | 5 threads | All stored; no orphan files | Gap |
| NF-CON-009 | 3 | Permission cleanup job running while a user is acting | Parallel | No exception | Gap |

## 5.2 Performance

Tools: k6 or JMeter against staging with the demo data multiplied (for example, 50 teams, 5,000 claims, 20,000 audit rows).

| ID | P | Scenario | Target |
|---|---|---|---|
| NF-PRF-001 | 2 | Login | p95 < 1 s (BCrypt cost 12 is about 250 ms by design) |
| NF-PRF-002 | 2 | Page loads (dashboard, queues, claim detail) | p95 < 800 ms at 20 concurrent users |
| NF-PRF-003 | 2 | Queue with 500 Submitted claims (in-memory sort) | p95 < 1.5 s; heap growth bounded |
| NF-PRF-004 | 2 | COO bulk approve of 50 claims | < 10 s; each in its own transaction |
| NF-PRF-005 | 2 | Audit log page with 100,000 rows | Paged; < 1 s with the indexes (check `EXPLAIN`) |
| NF-PRF-006 | 2 | Upload 10 MB | < 5 s on a 20 Mbps link; memory is not > 3× the file size (file is read into a byte array) |
| NF-PRF-007 | 3 | N+1 queries | Count the SQL statements for the queue and dashboard pages (`spring.jpa.show-sql` or a datasource proxy); assert the count does not grow with the row count |
| NF-PRF-008 | 3 | Soak | 8 h at low load: no memory growth, the login-attempt map does not grow without bound |
| NF-PRF-009 | 3 | Start-up time | < 60 s including Flyway on an empty DB |
| NF-PRF-010 | 3 | Notification badge polling (`/notifications/badge`) | A cheap query; < 100 ms |

## 5.3 Reliability and recovery (REC)

| ID | P | Scenario | Expected |
|---|---|---|---|
| NF-REC-001 | 1 | Kill the app during a transition | The transaction is rolled back: status, audit and notification are consistent |
| NF-REC-002 | 1 | Database unavailable | Friendly error page; `/actuator/health` DOWN; automatic recovery when the DB returns |
| NF-REC-003 | 2 | App restart | Sessions are lost (in-memory) and the login-attempt state is reset: document it. Documents and data remain |
| NF-REC-004 | 2 | Storage unavailable (disk full / S3 down) | Upload fails with a clear message; no DB row without a file |
| NF-REC-005 | 2 | SMTP unavailable | Actions succeed; in-app notifications are present; error logged (TC-NTF-010) |
| NF-REC-006 | 1 | Backup and restore | Restore the DB and the document store to a fresh instance; claims, audit and documents all match (check SHA-256 of the files) |
| NF-REC-007 | 2 | Migration on a copy of production data | Flyway applies cleanly; `validate` passes |
| NF-REC-008 | 3 | Clock change / DST (not applicable in IST) / server in UTC | Display stays in IST; expiry at 00:00 IST |

## 5.4 Usability

| ID | P | Check |
|---|---|---|
| NF-USA-001 | 1 | A new team member submits a first claim without help in < 10 minutes (hallway test with 3 people) |
| NF-USA-002 | 1 | Every validation error appears next to its field and is in plain language; the form keeps the entered data |
| NF-USA-003 | 1 | COO approves from a phone with one hand: row expands, Approve is reachable, a confirmation appears |
| NF-USA-004 | 2 | Status chips, timeline and next-step text are understandable without training |
| NF-USA-005 | 2 | Empty states have guidance ("No claims yet…") |
| NF-USA-006 | 2 | Success and failure messages (flash) are visible and don't vanish before reading |
| NF-USA-007 | 2 | The Back button and refresh don't re-submit or break (POST-redirect-GET) |
| NF-USA-008 | 3 | Currency, dates and codes are formatted consistently: `₹1,25,000.00`, `dd MMM yyyy` |

## 5.5 Accessibility (WCAG 2.1 AA)

| ID | P | Check | Tool |
|---|---|---|---|
| NF-ACC-001 | 2 | Colour contrast ≥ 4.5:1 for text on the green brand palette, status chips and the budget meter | Lighthouse, axe |
| NF-ACC-002 | 2 | All inputs have labels; errors are tied to the fields (`aria-describedby`) | axe |
| NF-ACC-003 | 2 | Full keyboard operation, visible focus, logical tab order, including the expandable approval rows | Manual |
| NF-ACC-004 | 2 | The HTMX updates announce changes (`aria-live`) | Screen reader (NVDA) |
| NF-ACC-005 | 3 | Logo and icons have alt text or are marked decorative | axe |
| NF-ACC-006 | 3 | Zoom to 200%, reflow at 320 px | Manual |
| NF-ACC-007 | 3 | Meaning isn't carried by colour alone (status chips also have text) | Manual |

## 5.6 Compatibility

| ID | P | Check |
|---|---|---|
| NF-CMP-001 | 1 | Chrome, Edge, Firefox (latest 2) and Safari (latest): the full flow in [02](02-black-box-test-cases.md) E2E-001 |
| NF-CMP-002 | 1 | Android Chrome and iOS Safari: COO approvals screen, claim form with camera upload |
| NF-CMP-003 | 2 | Viewports 360, 768, 1280, 1920 px: no horizontal scroll, readable tables |
| NF-CMP-004 | 2 | HEIC from an iPhone uploads and downloads (no preview) |
| NF-CMP-005 | 2 | CDN down (Tailwind from the CDN, ASSUMPTION in README): behaviour when the CDN is blocked. Record it as a deployment risk, and consider self-hosting the CSS |
| NF-CMP-006 | 3 | JavaScript disabled: forms still work (HTMX enhancement only) |
| NF-CMP-007 | 3 | Print view of a claim |

## 5.7 Localisation and data formats

| ID | P | Check |
|---|---|---|
| NF-LOC-001 | 2 | Indian digit grouping for every amount (lists, emails, flash messages, audit detail) |
| NF-LOC-002 | 2 | Dates in IST and `dd MMM yyyy` everywhere, including emails |
| NF-LOC-003 | 2 | Server in UTC and a user in another timezone: "today" for the expense date and the payment date is by IST |
| NF-LOC-004 | 3 | Unicode names (Tamil, Hindi) in vendor, team and user names, and file names |

## 5.8 Maintainability and operations

| ID | P | Check |
|---|---|---|
| NF-OPS-001 | 2 | Logs: JSON in `prod`, with the request id and user id and no secrets |
| NF-OPS-002 | 2 | `docker compose up --build` works from a clean clone; a documented port override works |
| NF-OPS-003 | 2 | The Docker image builds with the pom's Java version (ASSUMPTION #1) |
| NF-OPS-004 | 3 | `/actuator/health/liveness` and `/readiness` respond |
| NF-OPS-005 | 3 | Adding a permission without the migration fails start-up (`PermissionCatalogCheck`) |
| NF-OPS-006 | 3 | Static analysis: SpotBugs / Checkstyle with no new High findings |

## 5.9 Regression suite

Run on every change to `claim/`, `user/`, `config/` or `common/security`:

1. `mvn test` (all unit and integration tests).
2. `tests/smoke/smoke.sh` against a freshly started container.
3. Manual: TC-E2E-001, TC-E2E-007, TC-WF-037, TC-WF-040, TC-SEC-020.

Before each release, add TC-E2E-002..010, the security section 4.1–4.3 and 4.7, and NF-CMP-001/002.
