# Master Prompt — Nirmaan Reimbursement Platform

> Prompt used with Claude Code to build Phase 1 (foundation and MVP) of the project.
> Kept verbatim so the history of how the system was specified stays with the code.
> Techniques it applies are summarised in [README.md](README.md).

You are a senior Java engineer. Build a production-quality reimbursement platform for **Nirmaan, the pre-incubator at IIT Madras**. Read this whole prompt before writing any code. Work in phases (Section 9), finish each one with passing tests, and stop to summarise after each phase.

---

## 1. Problem

Nirmaan funds student startup teams. Teams spend their own money on approved expenses and then claim reimbursement. Today this runs entirely over email: teams send bills and payment proofs, the COO approves by reply, and Finance pays. Claims get lost, nobody knows a claim's status, the COO is very busy and becomes a bottleneck, and nobody checks rules or budgets consistently.

Goal: replace the email process with a web platform where:
- teams submit claims with documents,
- Nirmaan staff verify the claims,
- the COO approves them in seconds,
- Finance pays them,
- every step is tracked and visible.

Urgent cases get priority handling.

---

## 2. Tech stack (mandatory)

- **Java 21**, **Spring Boot 3.x**, **Maven**
- **Spring Web MVC** + **Thymeleaf** + **HTMX** for server-rendered, mobile-first UI. Use Tailwind CSS from the CDN or a small custom CSS file. Do not use a SPA framework.
- **Spring Security**: form login with BCrypt passwords and role-based access. Keep the auth layer pluggable so IITM SSO/OAuth2 can be added later.
- **Spring Data JPA** with **Hibernate** and **PostgreSQL 16**
- **Flyway** for all schema migrations. Never use `ddl-auto=update`.
- **Spring Validation** (Jakarta Bean Validation)
- **Spring Mail** for notifications, sent through an interface so it can be swapped. In development, log emails or use MailHog.
- **Apache PDFBox** for invoice text extraction. Leave Tesseract OCR as a later hook behind an interface.
- File storage behind a `StorageService` interface:
  - local-disk implementation for development,
  - S3-compatible implementation (MinIO) for production.
- **Spring Scheduler** for reminders and the daily digest
- **Apache POI** for Excel exports
- Testing: **JUnit 5**, **Mockito**, **Spring Boot Test**, **Testcontainers** (PostgreSQL)
- **Docker Compose** with the app, Postgres, MinIO and MailHog
- Lombok is optional. Prefer Java records for DTOs.
- Store all money as `BigDecimal` in INR, using `NUMERIC(12,2)` in the database.
- Store timestamps as UTC and display them in **Asia/Kolkata**.

---

## 3. Users and roles

| Role | Who | Can do |
|---|---|---|
| `COO` | Chief Operating Officer | **Super-user: has every permission in the system.** Owns staff management; see below |
| `NIRMAAN_STAFF` | Nirmaan employees | Only the permissions the COO grants them |
| `FINANCE` | Finance team | Only the permissions the COO grants them (default: payments and reports) |
| `TEAM_MEMBER` | Student startup founders (several per team) | Create and submit claims for their own team, request pre-approvals, reply to queries, see their team's budget and claim status |

- A team member sees **only** their own team's data. Enforce this at the service and query level, not only in the UI.

### COO powers and permission management
The COO is the owner of the platform. There is no separate admin role; all admin functions belong to the COO.

**The COO can do everything:**
- every action at every stage of the workflow, including verifying a claim and approving it in one step, and overriding or reopening any claim (reason required, audit-logged);
- every staff, finance and settings screen.

**The COO adds and manages Nirmaan employees and Finance users:**
- invite a user by email,
- assign their role,
- tick the exact permissions they get,
- edit those permissions at any time,
- deactivate the user.

**Use fine-grained permissions, not hard-coded roles.** Build authorisation on permissions with Spring Security authorities and `@PreAuthorize("hasAuthority('…')")`. A role is only a default bundle of permissions; the COO can grant or revoke each one per user.

Permissions:

| Permission | Meaning |
|---|---|
| `CLAIM_VERIFY` | Verify claims and forward them to the COO |
| `CLAIM_RETURN_REJECT` | Send claims back to the team or reject them |
| `CLAIM_APPROVE` | Approve claims on the COO's behalf (this is how delegation works) |
| `CLAIM_FLAG_PRIORITY` | Mark claims or teams as priority |
| `PREAPPROVAL_DECIDE` | Approve or reject pre-approval requests |
| `TEAM_MANAGE` | Create and edit teams and members, set budgets, promote Pratham → Akshar |
| `COHORT_MANAGE` | Create and edit cohorts |
| `BANK_DETAILS_VIEW` | See full bank account numbers |
| `BANK_DETAILS_VERIFY` | Verify team bank details |
| `PAYMENT_RECORD` | Mark claims paid, create payout batches |
| `REPORT_VIEW_EXPORT` | View analytics, export reports |
| `AUDIT_LOG_VIEW` | View the global audit log |
| `CATEGORY_MANAGE` | Edit expense categories and rules |
| `SETTINGS_MANAGE` | Edit thresholds, SLAs, email templates |
| `STAFF_MANAGE` | Invite staff and edit their permissions. **COO only, cannot be granted.** |

Default bundles, which the COO can change per user:
- `NIRMAAN_STAFF` gets `CLAIM_VERIFY`, `CLAIM_RETURN_REJECT`, `CLAIM_FLAG_PRIORITY`, `PREAPPROVAL_DECIDE`, `TEAM_MANAGE` and `COHORT_MANAGE`.
- `FINANCE` gets `PAYMENT_RECORD`, `BANK_DETAILS_VIEW` and `REPORT_VIEW_EXPORT`.

**Permission rules:**
- The COO account always holds every permission. The system must refuse to remove the COO's permissions, deactivate the last COO, or leave the platform without a COO.
- Hand-over: the COO can transfer the COO role to another user.
- Permissions granted to a user can be time-limited (e.g. `CLAIM_APPROVE` while the COO is on leave). They expire automatically.
- Every permission grant, revoke and expiry is audit-logged (who, to whom, which permission, when).
- Permission changes take effect on the user's next request. Reload authorities per request or invalidate their sessions; do not wait for re-login.
- A staff member can never approve a claim they verified themselves, unless the actor is the COO. This keeps a second pair of eyes on every claim.

---

## 4. Claim workflow (state machine)

```
DRAFT ──submit──▶ SUBMITTED ──staff verify──▶ VERIFIED ──COO approve──▶ APPROVED ──finance pay──▶ PAID
                     │                            │
                     └──staff return──▶ RETURNED ◀┘ COO return
                                          │
                                          └──team resubmit──▶ SUBMITTED
                     SUBMITTED / VERIFIED ──reject──▶ REJECTED (terminal)
                     VERIFIED ──COO partial approve──▶ APPROVED (approvedAmount < claimedAmount, reason required)
```

Rules for the state machine:
- Implement it in **one service class** that validates every transition: who may perform it and from which state.
- Reject illegal transitions with a clear exception.
- Every transition writes an **audit log** entry: actor, permission used, from-state, to-state, timestamp, comment.
- `RETURNED` and `REJECTED` require a reason. `RETURNED` claims can be edited and resubmitted by the team.
- **Auto-approval:**
  - Applies only when the claim is VERIFIED, its amount is ≤ the COO-configured threshold (default ₹0, meaning off), its category is allowed, it needs no pre-approval, and it is not flagged.
  - Such a claim moves to APPROVED automatically, with the system as actor and the note "auto-approved under threshold".

---

## 5. Business rules (from the Nirmaan Student Guide — enforce these)

### Budgets
- **Pratham** program: up to **₹2,00,000** per team (6 months).
- **Akshar** program: up to **₹5,00,000** per team (6 months).
- Budgets are configurable per team and per cohort.
- Remaining budget = budget − (sum of APPROVED + PAID approved amounts). Show the amount pending in SUBMITTED + VERIFIED claims separately.
- If a claim exceeds the remaining budget, the COO may **partially approve** it up to the remaining amount. Warn about this at submission time and at the COO's screen.
- This is seed funding, not a grant: teams spend first and are reimbursed after.

### Categories
Store categories as data, editable by the COO (or anyone with `CATEGORY_MANAGE`).
- **Allowed:**
  - Consumables and prototype parts (parts that become part of the product)
  - Market research
  - Subscriptions or software for product development (pre-approval recommended)
  - Other (justification required)
- **Requires pre-approval:** Travel or trips that benefit the startup.
- **Not allowed:**
  - Hiring interns
  - Tools used to build the prototype that are not part of the product
  - Courses, skill development or training
  - Marketing and promotion
  - External event participation or stalls
  - Accommodation

When a team picks a disallowed category, show a clear warning explaining the rule and block submission.

### Pre-approval
- A team requests pre-approval with:
  - category,
  - estimated amount,
  - purpose and justification,
  - expected date.
- Staff approve or reject it, or forward it to the COO.
- A claim in a pre-approval category **must link** to an approved pre-approval for the same team. The claim amount may exceed the pre-approved estimate by at most a configurable percentage (default 10%); above that, the claim is flagged.

### Justification
Every claim needs a short explanation of how the expense benefits the project. This is mandatory per the guide.

### Required documents per claim
- **Invoice** (PDF or image). Must be a real invoice. **Pro-forma invoices are not accepted.**
- **Payment proof**: UPI screenshot, bank statement extract or card slip.
- Optional extra attachments.
- Max 10 MB per file. Allowed types: pdf, jpg, png, heic. Store a checksum (SHA-256) for each file.

### Invoice checklist
Run automatic checks where possible (PDFBox text extraction); staff confirm the rest manually. The invoice must contain:
1. The word "Invoice", and must not be pro-forma (flag the text "pro-forma" / "proforma").
2. Seller name, address and contact.
3. GST, PAN or company registration number. Validate the GSTIN format `\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]` and the PAN format.
4. Buyer name and address.
5. Invoice date (and due or delivery date if present).
6. Unique invoice number.
7. Line items with quantity, unit cost and total.
8. Total amount with tax information.

Show each check as pass, fail or unknown on the staff verification screen.

### Duplicate detection
Flag a claim, with a link to the matching claim, when any of these match:
- same seller GSTIN or name + invoice number,
- same file checksum,
- same team + amount + invoice date.

---

## 6. Priority handling

- `NIRMAAN_STAFF` can mark an individual claim, or a whole team, as **priority**, with a **mandatory reason**. Example: a student cannot afford to wait, or a large out-of-pocket amount.
- Priority items:
  - sort to the top of every queue,
  - carry a visible badge,
  - have a shorter target time (configurable; default 2 working days per stage vs 5 for normal).
- Teams can **request** priority with a reason; staff accept or decline. Teams cannot set priority themselves.
- All priority changes are audit-logged.

---

## 7. Features by role

### Team member
- Dashboard:
  - budget meter (total, approved, paid, pending, remaining),
  - claims list with status chips,
  - pre-approvals.
- Claim form:
  - amount, expense date, vendor, category, justification,
  - linked pre-approval,
  - uploads.
  - Save as DRAFT. When the invoice is a PDF with extractable text, pre-fill vendor, invoice number, date and amount from it.
- Live warnings on the form for:
  - disallowed category,
  - missing pre-approval,
  - over budget,
  - possible duplicate.
- Claim detail page:
  - status timeline,
  - **comment thread** with staff, COO and Finance (this replaces email),
  - edit and resubmit when RETURNED.
- One-time bank details form: account holder, account number, IFSC, bank name. Mask the account number in the UI and allow edits only with staff re-verification.
- Notifications (in-app + email) on every status change and new comment.

### Nirmaan staff
- Verification queue: priority first, then oldest first. Filters for team, cohort, category, status and age.
- Verification screen:
  - document viewer side-by-side with the claim data,
  - invoice checklist results,
  - duplicate warnings,
  - team budget snapshot.
  - Actions: Verify (forward to COO), Return (reason), Reject (reason), Flag priority.
- Pre-approval queue.
- Team and cohort management:
  - create cohorts,
  - add teams and members,
  - set program (PRATHAM/AKSHAR) and budget,
  - **promote a team from Pratham to Akshar** (new budget, history kept).
- User invites by email.

### COO (optimise everything here for speed)
- **One mobile-first approval screen**:
  - priority section on top, then everything else,
  - each row is one line: team, amount, category, age, "verified by X", priority badge.
  - Tap a row to expand it with the justification, a document thumbnail and the budget impact.
- One-tap Approve / Reject / Return, **partial approve** with an amount, and **bulk approve** of selected rows.
- **Daily digest email** (configurable time, default 9:00 IST), e.g. "N claims waiting (M priority), total ₹X". Each item has a deep link.
- **Secure signed email links** to approve or reject directly from the email:
  - single-use token,
  - expires in 72 h,
  - shows a confirmation page before acting.
- Settings:
  - auto-approval threshold,
  - delegation (grant `CLAIM_APPROVE` to a staff member for a date range; their approvals are logged as "on behalf of COO").
- By default the approval screen shows only verified claims. The COO can also open the full staff queue and act on any claim directly.
- **Staff & Permissions page (COO only):**
  - list of all Nirmaan employees and Finance users with their role, permissions and last activity,
  - invite user,
  - permission checkboxes per user, with optional expiry date,
  - deactivate user,
  - transfer the COO role,
  - permission change history.

### Finance
- Payment queue of APPROVED claims, priority first. Shows bank details, the full account number on click with access logged, and the approved amount.
- **Mark as paid**: payment date, mode (NEFT/IMPS/UPI), transaction reference/UTR (required). The team is notified.
- **Batch payout**: select claims and export Excel/CSV in a bank bulk-upload friendly layout. Mark the whole batch paid.
- Reports, exportable to Excel:
  - spend by team, cohort, category and month,
  - outstanding approved-but-unpaid amounts.

### Settings (COO, or anyone granted `CATEGORY_MANAGE` / `SETTINGS_MANAGE`)
- Manage categories, budget defaults, SLA targets and email templates.

### System-wide
- **Audit log** for every action on claims, pre-approvals, priority, bank details and settings. Append-only; viewable per claim, and globally by the COO or anyone with `AUDIT_LOG_VIEW`.
- **SLA reminders** (scheduled job): nudge the responsible role when a claim sits in a stage beyond its target. Escalate to staff if a COO item is overdue.
- **Analytics page** (COO, or anyone with `REPORT_VIEW_EXPORT`):
  - average turnaround per stage,
  - claims per status,
  - top categories,
  - budget utilisation per team.
- Global search by claim ID, team, vendor or invoice number.
- Human-readable claim IDs, e.g. `NRM-2026-00042`.

---

## 8. Data model (minimum — refine as needed)

- `users` (id, name, email unique, password_hash, active, created_at)
- `user_roles` (user_id, role)
- `permissions` (code, description). Seed it via a Flyway migration and keep it in sync with a `Permission` enum.
- `user_permissions` (user_id, permission_code, granted_by, granted_at, expires_at nullable)
- `role_default_permissions` (role, permission_code). Editable by the COO.
- `cohorts` (id, name e.g. "Pratham Jan 2026", start_date, end_date)
- `teams` (id, name, cohort_id, program PRATHAM|AKSHAR, budget_amount, priority boolean, priority_reason, active)
- `team_members` (team_id, user_id)
- `bank_details` (team_id, holder_name, account_number encrypted, ifsc, bank_name, verified_by, verified_at)
- `categories` (id, name, allowed, requires_pre_approval, description)
- `pre_approvals` (id, team_id, category_id, estimated_amount, purpose, expected_date, status, decided_by, decided_at, reason)
- `claims`:
  - id, public_code, team_id, submitted_by, category_id, pre_approval_id nullable
  - claimed_amount, approved_amount
  - expense_date, vendor_name, vendor_gstin, invoice_number, invoice_date
  - justification, status, priority, priority_reason
  - created_at, submitted_at, verified_at, approved_at, paid_at
  - `@Version` for optimistic locking
- `claim_documents` (id, claim_id, type INVOICE|PAYMENT_PROOF|OTHER, storage_key, original_filename, content_type, size, sha256, uploaded_at)
- `invoice_checks` (claim_id, check_name, result PASS|FAIL|UNKNOWN, detail)
- `comments` (id, claim_id, author_id, body, created_at)
- `payments` (id, claim_id, batch_id nullable, amount, paid_on, mode, utr, recorded_by)
- `payout_batches` (id, created_by, created_at, status)
- `audit_log` (id, entity_type, entity_id, action, actor_id, on_behalf_of_id, from_state, to_state, detail jsonb, created_at)
- `notifications` (id, user_id, message, link, read, created_at)
- `approval_tokens` (token_hash, claim_id, action, expires_at, used_at)
- `settings` (key, value)

Encrypt bank account numbers at rest with a JPA AttributeConverter (AES-GCM, key from env).

---

## 9. Build phases

**Phase 1 — Foundation and MVP**
- Project skeleton, Docker Compose, Flyway, and security with **permission-based authorisation**.
- COO Staff & Permissions page: invite users, grant and revoke permissions.
- Seed data: one COO, one staff, one finance user, demo team members, one cohort, three teams.
- Bootstrap the first COO account from environment variables on first start.
- Teams, cohorts, categories.
- Claim create, upload and submit, with the full state machine:
  - staff verify / return / reject,
  - COO approve / reject / partial,
  - finance mark paid.
- Budget meter, priority flag, comment thread, audit log.
- In-app notifications and email notifications.
- COO mobile approval screen with bulk approve.

**Phase 2 — Rules and checks**
- Category rules and warnings.
- Pre-approvals.
- Invoice checklist with PDFBox extraction and pre-fill.
- Duplicate detection.
- Bank details with encryption.
- Finance batch export.

**Phase 3 — COO speed and operations**
- Daily digest.
- Signed email approve links.
- Auto-approval threshold.
- Delegation.
- SLA reminders and escalation.
- Pratham → Akshar promotion.

**Phase 4 — Insight**
- Analytics page, reports and Excel exports.
- Global search.
- OCR hook for image invoices.

---

## 10. Project structure

Package by feature, not by layer:

```
com.nirmaan.reimburse
 ├─ config/        (security, storage, mail, scheduling, web)
 ├─ common/        (audit, money, time, exceptions, storage service)
 ├─ user/  team/  cohort/  category/
 ├─ claim/         (entity, repository, service, ClaimStateMachine, controller, dto, views)
 ├─ preapproval/  document/  invoicecheck/  payment/  notification/  report/
 └─ NirmaanReimburseApplication.java
src/main/resources/
 ├─ db/migration/  (Flyway V1__init.sql …)
 ├─ templates/     (Thymeleaf, with fragments for HTMX)
 └─ static/
```

Layering rules:
- Controllers stay thin.
- Business rules live in services.
- Never expose entities to templates; use DTOs and records.

---

## 11. Quality requirements

- **Security:**
  - CSRF on,
  - authorisation checks in services (a team member must never read another team's claim; write a test for this),
  - file-type validation by content (magic bytes), not by extension,
  - uploaded files served only through an authorised controller, never as public static files,
  - rate-limit login,
  - secrets from environment variables only.
- **Tests:**
  - permission tests: every protected endpoint and service method is denied without its permission and allowed with it; the COO can do everything; nobody else can get `STAFF_MANAGE`; the last COO cannot be removed; expired grants stop working; a verifier cannot approve their own verified claim,
  - unit tests for the state machine (every legal and illegal transition), budget maths, partial approval, duplicate detection, invoice checks and priority sorting,
  - integration tests with Testcontainers for the full happy path (submit → verify → approve → pay) and for access control.
  - Target ≥ 80% coverage on services.
- **Concurrency:** optimistic locking on claims, so two approvers cannot double-act.
- **UX:**
  - mobile-first,
  - accessible (labels, contrast, keyboard),
  - Indian number formatting (₹1,25,000.00),
  - IST dates (dd MMM yyyy),
  - clear empty states and error messages.
- **Observability:** structured logging, Spring Boot Actuator health endpoint.
- **Docs:** a README with setup (`docker compose up`), demo logins per role, architecture overview, and how to add a category or change budgets.

---

## 12. How to work

1. Restate the plan for the current phase in a short list, then implement it.
2. Write the Flyway migrations first, then entities, services with tests, controllers, and finally templates.
3. Run the build and tests after each feature. Do not move on with failing tests.
4. When a requirement is ambiguous, choose the simplest option that fits the business rules above, note the assumption in `ASSUMPTIONS.md`, and continue.
5. After each phase, summarise:
   - what was built,
   - how to try it (demo user + steps),
   - what's next.

Start with **Phase 1** now.
