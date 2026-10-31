# 4. Security Test Cases

Reference: OWASP ASVS and the OWASP Top 10. The platform handles money and student data, so every case here is **P1** unless marked.

## 4.1 Authentication

| ID | Check | Steps | Expected | Auto |
|---|---|---|---|---|
| TC-SEC-001 | Passwords hashed | Inspect `users.password_hash` | BCrypt (`$2a$12$…`); never plain text | Gap |
| TC-SEC-002 | Brute force | 5 wrong passwords | Lock for 15 min per email | StaffManagementIT `loginIsRateLimited` |
| TC-SEC-003 | No user enumeration | Wrong email vs wrong password | Same page, same message, similar timing | Gap |
| TC-SEC-004 | Session fixation | Note `JSESSIONID`, log in | A new session id after login (`changeSessionId`) | Gap |
| TC-SEC-005 | Cookie flags | Inspect the cookie | `HttpOnly`, `SameSite=Lax`; `Secure` over HTTPS (set in prod) | Manual |
| TC-SEC-006 | Logout invalidates | Copy the cookie, log out, replay | Redirect to login | Gap |
| TC-SEC-007 | Deactivated user | Deactivate, replay the old session | Signed out | StaffManagementIT |
| TC-SEC-008 | Invite token | Single use, random, expires, not guessable | Used/expired link rejected | StaffManagementIT |
| TC-SEC-009 | Rate limit bypass | Vary email case/whitespace (`A@x`, ` a@x`) | Still counted as the same account | Gap |
| TC-SEC-010 | Password reset (if present) / password in logs | Search the logs for passwords or tokens | None logged | Manual |

## 4.2 Authorisation (vertical)

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-011 | **Permission matrix:** for every protected endpoint, call it with a user who lacks the permission, and with one who has it | 403 / success | PermissionMatrixIT |
| TC-SEC-012 | `STAFF_MANAGE` screens: COO only, even for staff with every grantable permission | 403 | PermissionMatrixIT |
| TC-SEC-013 | Service-method security: call the services directly without the permission | `AccessDeniedException` | PermissionMatrixIT |
| TC-SEC-014 | Team member calls back-office URLs (`/staff/queue`, `/coo/approvals`, `/finance/payments`, `/admin/staff`, `/audit`, `/categories`, `/cohorts`, `/teams`) | 403 | AccessControlIT |
| TC-SEC-015 | Back-office user calls `/team` (team dashboard) | 403 | PermissionMatrixIT |
| TC-SEC-016 | Direct POST of a workflow action by a team member | 403 | AccessControlIT |
| TC-SEC-017 | Permission changes apply on the next request | Immediately | StaffManagementIT |
| TC-SEC-018 | Expired permission grant | Denied from the expiry time | StaffManagementIT |
| TC-SEC-019 | Role/parameter tampering: change `role`, `permissions`, `userId`, `teamId`, `status` or `approvedAmount` in hidden or extra form fields (mass assignment) | Ignored or rejected | Gap |

## 4.3 Authorisation (horizontal) and data isolation

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-020 | Team B reads Team A's claim by URL (`/claims/{id}`), edit page, comments, documents | 403/404 with no Team A data | AccessControlIT |
| TC-SEC-021 | Team B writes to Team A's claim (update, delete, upload, comment, submit) | Denied | AccessControlIT |
| TC-SEC-022 | Same checks directly at the service layer | `AccessDeniedException` / `NotFoundException` | AccessControlIT |
| TC-SEC-023 | ID enumeration: iterate claim/document/notification ids | Responses for foreign ids are identical to those for non-existent ids (no oracle) | Gap |
| TC-SEC-024 | Notifications: open or mark read another user's notification | Denied | Gap |
| TC-SEC-025 | Team member sees other teams in dropdowns/lists/search/budget | No | Gap |
| TC-SEC-026 | Removed team member keeps access? | No (deactivated) | Gap |

## 4.4 Input handling and injection

| ID | Check | Payload | Expected | Auto |
|---|---|---|---|---|
| TC-SEC-030 | Stored XSS in vendor, justification, comment, team name, category name, rejection reason, file name | `<script>alert(1)</script>`, `"><img src=x onerror=alert(1)>` | Rendered escaped (Thymeleaf `th:text`); no `th:utext` on user data | Gap (also `grep -r utext src/main/resources/templates`) |
| TC-SEC-031 | SQL injection in text and query/filter parameters | `' OR 1=1 --`, `1; DROP TABLE claims` | No error, no effect (JPA parameters) | Gap |
| TC-SEC-032 | Path traversal in file name and storage key | `../../x.pdf` | Safe name; random storage key | FileTypeDetectorTest `sanitisesFilenames` |
| TC-SEC-033 | Open redirect | Add `?next=https://evil.com` to `/login` or the notification open URL | Ignored (`alwaysUseDefaultTargetUrl`) | Gap |
| TC-SEC-034 | HTTP parameter pollution | Duplicate `claimedAmount=1&claimedAmount=99999` | Deterministic; validated | Gap |
| TC-SEC-035 | Oversized body / parameters | 30 MB request; 1 MB text field | Rejected cleanly (413 or validation); no 500 | Gap |
| TC-SEC-036 | Unicode tricks | Right-to-left override in file names, zero-width characters in names | Displayed safely | Gap |
| TC-SEC-037 | CSV/formula injection | Vendor `=HYPERLINK(...)` (relevant when exports are added in Phase 2) | Prefix with `'` in exports | Gap (Phase 2) |

## 4.5 CSRF and headers

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-040 | POST without the CSRF token | 403 | AccessControlIT `csrfIsRequired` |
| TC-SEC-041 | HTMX requests include `X-CSRF-TOKEN` | Succeed | Manual |
| TC-SEC-042 | Every `<form method=post>` has a token | Yes (Thymeleaf auto-adds) | Gap (template scan) |
| TC-SEC-043 | Headers on pages: `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: same-origin`, `X-Content-Type-Options: nosniff` | Present | Gap |
| TC-SEC-044 | Document response: `nosniff`, `no-store`, `Content-Disposition` set, content type from the detected value (not the browser's) | Present | AccessControlIT (partial) |
| TC-SEC-045 | Caching of authenticated pages | `Cache-Control: no-store` (Spring default for secured pages) | Gap |
| TC-SEC-046 | CSP | Not set today (Tailwind CDN, inline). Record as a **known gap**; plan for a nonce-based CSP before production (P2) | Known gap |
| TC-SEC-047 | HTTPS / HSTS | Behind a TLS proxy: HSTS set, cookie `Secure` | Manual (staging) |

## 4.6 File upload

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-050 | Disguised file types (see TC-DOC-010..012) | Rejected by content | FileTypeDetectorTest |
| TC-SEC-051 | Polyglot: a PDF containing JavaScript | Accepted (valid PDF) but served as `application/pdf` with `nosniff`, and never inline-executed as HTML | Manual |
| TC-SEC-052 | SVG / HTML / `.js` | Rejected | Gap |
| TC-SEC-053 | Zip bomb / giant image dimensions | Not parsed in Phase 1 (only magic bytes). Revisit in Phase 2 with PDFBox | Note |
| TC-SEC-054 | Download from another team | Denied | AccessControlIT |
| TC-SEC-055 | Storage keys not guessable | UUID | Manual |
| TC-SEC-056 | S3 bucket is private; direct object URL gives 403 | | Manual (staging) |

## 4.7 Business-logic abuse

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-060 | Verifier approves their own claim | Denied (four-eyes) | StaffManagementIT, ClaimStateMachineTest |
| TC-SEC-061 | Staff approves a Submitted claim, skipping verification | Denied (only the COO) | ClaimStateMachineTest |
| TC-SEC-062 | Replay: resend an old approval request | `ConcurrentUpdate` / illegal state | PlatformIT |
| TC-SEC-063 | Approve more than claimed or more than the budget | Refused | ClaimStateMachineTest, PlatformIT |
| TC-SEC-064 | Mark paid twice, or pay an unapproved claim | Refused | Gap |
| TC-SEC-065 | Edit the amount after approval (via a hand-made POST to `/claims/{id}`) | Refused | ServiceFlowsIT `draftsCanBeEdited…` (partial) |
| TC-SEC-066 | A team member edits their own claim's status or approved amount via extra fields | Ignored | Gap |
| TC-SEC-067 | Race: two parallel approvals overspend the budget | One refused | PlatformIT |
| TC-SEC-068 | Race: delete a draft while submitting it | One wins; consistent state | Gap |
| TC-SEC-069 | Self-promotion: a staff user grants themselves a permission | Denied (`STAFF_MANAGE` COO only) | PermissionMatrixIT |

## 4.8 Audit and logging

| ID | Check | Expected | Auto |
|---|---|---|---|
| TC-SEC-070 | Audit log append-only (trigger) | Update/delete fail, including from the application DB user | PlatformIT |
| TC-SEC-071 | Every privileged action is logged with the actor, permission used and on-behalf-of | Yes | HappyPathIT, StaffManagementIT |
| TC-SEC-072 | No secrets in the logs (passwords, tokens, S3 keys, full bank numbers) | None | Manual |
| TC-SEC-073 | Error pages | No stack trace, SQL or paths shown (`error.html`; whitelabel off) | Gap |
| TC-SEC-074 | Audit detail of comments stores the length, not the text | As designed | Manual |

## 4.9 Configuration and deployment

| ID | Check | Expected |
|---|---|---|
| TC-SEC-080 | Production profile: `DEMO_DATA=false`, no demo accounts, strong `BOOTSTRAP_COO_PASSWORD` | Confirmed before go-live |
| TC-SEC-081 | Actuator exposure: only `health` and `info` | `/actuator/env`, `/actuator/beans` return 404 |
| TC-SEC-082 | Secrets only in environment variables, not in the repo or image | `git grep -i "secret\|password"` review |
| TC-SEC-083 | Dependency scan | `mvn org.owasp:dependency-check-maven:check` has no High/Critical |
| TC-SEC-084 | Container runs as non-root, minimal image | `Dockerfile` review |
| TC-SEC-085 | Single-instance limits documented (in-memory rate limit) | See ASSUMPTIONS #2 |
| TC-SEC-086 | Automated scan | OWASP ZAP baseline against staging: no High findings |

## 4.10 Optional ZAP baseline command

```bash
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t http://host.docker.internal:8080 -r zap-report.html
```
