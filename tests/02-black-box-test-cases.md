# 2. Black-Box Test Cases

Tests are written from the requirements (README, ASSUMPTIONS) with no knowledge of the code. Demo accounts and data are in [06-test-data.md](06-test-data.md). The password for all demo accounts is `Demo@1234`.

Column key: **P** = priority (1–3). **Auto** = existing automated coverage (see [07](07-traceability-matrix.md)); **~** = inferred from test names, to be confirmed; **Gap** = no automated test found.

---

## 2.1 Authentication and session (AUTH)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-AUTH-001 | **Valid login, each role.** Log in with each demo account | Lands on: COO → Approvals; staff → Verification queue; finance → Payment queue; team → Team dashboard | P1 · HappyPathIT ~ |
| TC-AUTH-002 | **Wrong password.** Valid email, wrong password | Stays on `/login?error`; generic message (does not say which field is wrong) | P1 · Gap |
| TC-AUTH-003 | **Unknown email.** Email not in system | Same message as TC-AUTH-002 (no user enumeration) | P1 · Gap |
| TC-AUTH-004 | **Lock after 5 failures.** 5 wrong passwords for one email, then the correct password | 6th attempt blocked even with correct password | P1 · StaffManagementIT `loginIsRateLimited` |
| TC-AUTH-005 | **Lock expires after 15 min.** After TC-AUTH-004 wait 15 min (or advance clock) | Login works again | P2 · LoginAttemptServiceTest |
| TC-AUTH-006 | **Success resets counter.** 4 failures, 1 success, 4 failures | Not blocked | P2 · LoginAttemptServiceTest |
| TC-AUTH-007 | **Lock is per email.** Lock `a@x`; log in as `b@x` | `b@x` unaffected | P2 · LoginAttemptServiceTest |
| TC-AUTH-008 | **Anonymous access.** Open `/`, `/claims/1`, `/coo/approvals` without a session | Redirect to `/login` | P1 · AccessControlIT |
| TC-AUTH-009 | **Logout.** Click Log out, press Back | Redirect to `/login?logout`; protected page redirects to login | P2 · Manual |
| TC-AUTH-010 | **Deactivated user.** COO deactivates user while they are signed in; user clicks any link | Signed out on the next request | P2 · StaffManagementIT ~ |
| TC-AUTH-011 | **Session timeout.** Idle for more than 8 h | Next request redirects to login | P3 · Manual |
| TC-AUTH-012 | **Email case.** Log in with `PRIYA@AgriSense.local` | Same result as lower case (document actual behaviour) | P2 · Gap |
| TC-AUTH-013 | **Public resources.** Open `/css/app.css`, `/img/nirmaan-logo.png`, `/fonts/*`, `/actuator/health` without login | 200 | P3 · Manual |
| TC-AUTH-014 | **Landing by role.** Open `/` for each role | Same landing as TC-AUTH-001 | P3 · HappyPathIT ~ |

## 2.2 Staff invitation and activation (INV)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-INV-001 | **Invite staff.** COO → Staff & permissions → Invite (name, email, role = Nirmaan staff) | User created inactive; email with link in MailHog; default staff permissions copied as explicit grants | P1 · StaffManagementIT `invitedStaff…` |
| TC-INV-002 | **Activate with link.** Open the link, set a valid password | Account active; can sign in | P1 · StaffManagementIT ~ |
| TC-INV-003 | **Link is single-use.** Open the same link after activation | Rejected (invalid or used) | P1 · StaffManagementIT ~ |
| TC-INV-004 | **Link for unknown/garbage token.** `/invite/zzz` | Clear error page, no stack trace | P2 · Gap |
| TC-INV-005 | **Expired link.** Wait past expiry (or advance clock) | Rejected; COO can resend | P2 · Gap |
| TC-INV-006 | **Resend invite.** COO → Resend invite | New link works; the old link no longer works | P2 · Manual |
| TC-INV-007 | **Duplicate email.** Invite an email already in use | Validation error; no second user | P1 · Gap |
| TC-INV-008 | **Invalid email.** `abc`, `a@`, empty, 255 chars | Field errors; nothing created | P2 · Gap |
| TC-INV-009 | **Name limit.** Name of 120 chars OK; 121 rejected | Boundary honoured | P2 · Gap |
| TC-INV-010 | **Cannot invite as COO or team member.** Tamper the role field to `COO` | Rejected (only Nirmaan staff and Finance are invitable) | P1 · StaffManagementIT ~ |
| TC-INV-011 | **Only COO can invite.** Staff with every grantable permission POSTs `/admin/staff/invite` | 403 | P1 · StaffManagementIT |
| TC-INV-012 | **Password policy on activation.** Empty / short password / mismatched confirmation | Rejected with message (document the policy) | P2 · Gap |

## 2.3 Staff, permissions and COO hand-over (USR)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-USR-001 | **Staff list is COO-only.** Staff / finance / team user opens `/admin/staff` | 403 | P1 · StaffManagementIT |
| TC-USR-010 | **Grant a permission.** COO ticks `CLAIM_APPROVE` for staff | Saved; audit `PERMISSION_GRANTED` | P1 · StaffManagementIT ~ |
| TC-USR-011 | **Revoke a permission.** COO unticks | Saved; audit `PERMISSION_REVOKED` | P1 · StaffManagementIT ~ |
| TC-USR-012 | **`STAFF_MANAGE` can't be granted.** Tamper the form to include `STAFF_MANAGE` | Rejected (service and DB `CHECK`) | P1 · StaffManagementIT `nobodyCanBeGrantedStaffManage` |
| TC-USR-013 | **Team member can't receive back-office permissions.** Grant any permission to a team member | Refused | P1 · PermissionServiceTest |
| TC-USR-014 | **COO's permissions can't be removed.** Edit the COO's grants | COO still holds all permissions | P1 · StaffManagementIT ~ |
| TC-USR-015 | **Change role defaults.** COO edits the staff default bundle | Affects only users invited afterwards | P2 · Gap |
| TC-USR-020 | **Change applies on next request.** Staff signed in; COO grants `CLAIM_APPROVE`; staff clicks Approvals | Page now accessible, no re-login | P1 · StaffManagementIT ~ |
| TC-USR-021 | **Revoke applies on next request.** Reverse of TC-USR-020 | 403 on next click | P1 · StaffManagementIT ~ |
| TC-USR-022 | **Grant with expiry, last day inclusive.** Grant with expiry = today | Works until 23:59:59 IST today; stops at 00:00 IST tomorrow | P1 · StaffManagementIT `expiredGrants…` |
| TC-USR-023 | **Expired grant audit.** After expiry the cleanup job runs (every minute) | Grant deleted; audit `PERMISSION_EXPIRED` with system actor | P1 · StaffManagementIT ~ |
| TC-USR-024 | **Expiry date in the past.** Choose yesterday | Rejected or immediately inactive (document) | P2 · Gap |
| TC-USR-030 | **Deactivate staff.** COO deactivates | Cannot log in; sessions invalid | P1 · StaffManagementIT ~ |
| TC-USR-031 | **Reactivate.** COO reactivates | Can log in again | P2 · ServiceFlowsIT `staffCanBeDeactivatedAndReactivated` |
| TC-USR-032 | **Last COO can't be deactivated.** COO deactivates self | Refused | P1 · StaffManagementIT ~ |
| TC-USR-040 | **Hand over COO.** COO → Transfer to an active staff user | New user is COO with all permissions; former COO becomes Nirmaan staff with the current default bundle; the new COO's explicit grants cleared | P1 · StaffManagementIT `cooHandOver…` |
| TC-USR-041 | **Hand back.** New COO transfers back | Works; exactly one COO exists at any time | P1 · StaffManagementIT ~ |
| TC-USR-042 | **Hand over to a team member.** Choose a team member | Refused | P1 · StaffManagementIT ~ |
| TC-USR-043 | **Hand over to an inactive user.** Choose a deactivated user | Refused | P2 · Gap |
| TC-USR-044 | **Hand-over audit.** After TC-USR-040 open the audit log | Entry with old and new COO | P2 · Gap |
| TC-USR-045 | **Bootstrap COO.** Start with `BOOTSTRAP_COO_*` set and no COO | COO exists; second start does not create another | P1 · StaffManagementIT ~ |

## 2.4 Cohorts, teams, members and budgets (TEAM)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-TEAM-001 | **Create cohort.** Staff with `COHORT_MANAGE` creates cohort | Appears in list; audited | P2 · ServiceFlowsIT `staffManageTeams…` |
| TC-TEAM-002 | **Cohort needs permission.** Staff without `COHORT_MANAGE` POSTs `/cohorts` | 403 | P1 · PermissionMatrixIT |
| TC-TEAM-003 | **Team member can't see cohorts.** Team user opens `/cohorts` | 403 | P2 · PermissionMatrixIT |
| TC-TEAM-010 | **Create team (Pratham).** `TEAM_MANAGE` creates team, budget empty | Budget = default ₹2,00,000 | P1 · ServiceFlowsIT ~ |
| TC-TEAM-011 | **Create team (Akshar).** Program = Akshar, budget empty | Default ₹5,00,000 | P1 · ServiceFlowsIT ~ |
| TC-TEAM-012 | **Custom budget.** Budget = 75000.50 | Used instead of the default | P1 · ServiceFlowsIT ~ |
| TC-TEAM-013 | **Budget boundaries.** 0.00 OK; -0.01 rejected; 10 digits + 2 decimals OK; 11 digits rejected; 3 decimals rejected | As stated | P1 · Gap |
| TC-TEAM-014 | **Name required / 120 max.** Empty, 120, 121 chars | Reject, accept, reject | P2 · Gap |
| TC-TEAM-015 | **Change program and budget.** Edit team | Saved; audited with old/new | P2 · ServiceFlowsIT ~ |
| TC-TEAM-016 | **Settings change affects defaults.** `update settings … budget.default.pratham` | New teams (budget empty) use the new default; existing teams with an explicit budget unchanged | P1 · Gap |
| TC-TEAM-020 | **Add member.** Add student by name/email | Student can sign in; sees only this team | P1 · ServiceFlowsIT ~ |
| TC-TEAM-021 | **One team per student.** Add the same student to a second team | Refused (unique index) | P1 · ServiceFlowsIT ~ |
| TC-TEAM-022 | **Remove member.** Remove member | Account deactivated; their claims stay with the team | P2 · ServiceFlowsIT ~ |
| TC-TEAM-023 | **Add staff email as member.** Use an existing staff email | Refused (document message) | P2 · Gap |
| TC-TEAM-024 | **Team member can't manage teams.** POST `/teams`, `/teams/{id}/members` as team user | 403 | P1 · PermissionMatrixIT |
| TC-TEAM-025 | **Team list/detail for staff.** Open `/teams`, `/teams/{id}` | Budget, spent, remaining, members shown | P2 · Manual |

## 2.5 Expense categories (CAT)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-CAT-001 | **List categories.** `CATEGORY_MANAGE` opens `/categories` | All seeded categories with flags | P2 · Manual |
| TC-CAT-002 | **Permission needed.** Staff without `CATEGORY_MANAGE` POSTs | 403 | P1 · PermissionMatrixIT |
| TC-CAT-003 | **Add category.** Name + flags | Appears in the claim form | P2 · ServiceFlowsIT `staffManage…` |
| TC-CAT-004 | **Deactivate.** Untick Active | Hidden for new claims; existing claims still display it; submitting an existing draft in this category is refused ("no longer available") | P2 · ServiceFlowsIT ~ |
| TC-CAT-005 | **Not reimbursable (Accommodation).** Choose it in the claim form | Blocking warning; submit refused with the category description | P1 · ServiceFlowsIT / ClaimWarningsTest |
| TC-CAT-006 | **Requires pre-approval (Travel).** Submit without pre-approval | Refused ("must be linked to an approved pre-approval") | P1 · ServiceFlowsIT ~ |
| TC-CAT-007 | **"Other" needs 30-char justification.** Justification of 29 chars → refused; 30 chars → accepted | Boundary | P1 · ClaimStateMachineTest |
| TC-CAT-008 | **Duplicate name.** Add a category with an existing name | Rejected | P3 · Gap |

## 2.6 Claims: create, edit, delete (CLM)

Field rules: category required; amount ≥ ₹1.00, max 10 integer digits + 2 decimals; expense date required, not in the future (IST); vendor required, ≤ 200; GSTIN optional, pattern `^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$`; invoice no. ≤ 60; justification required, ≤ 2000.

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-CLM-001 | **Create draft.** Team member fills all required fields, Save | Draft with code `NRM-<year>-NNNNN`; status Draft | P1 · HappyPathIT ~ |
| TC-CLM-002 | **Claim code format and uniqueness.** Create several claims | Codes unique, sequential, five digits, year in IST | P1 · PlatformIT `claimCodesAreHumanReadableAndUnique` |
| TC-CLM-003 | **Missing required fields.** Submit the form with each required field empty (one at a time) | Field-specific message; nothing saved | P1 · Gap |
| TC-CLM-004 | **Amount boundaries.** 0.99 ✗, 1.00 ✓, 1.01 ✓, 9999999999.99 ✓, 10000000000.00 ✗, 12.345 ✗, -5 ✗, `abc` ✗, empty ✗ | As marked | P1 · Gap |
| TC-CLM-005 | **Expense date boundaries.** Today ✓, yesterday ✓, tomorrow ✗ (IST: also test just after 18:30 UTC) | As marked | P1 · ClaimStateMachineTest (partial) |
| TC-CLM-006 | **Vendor length.** 200 ✓, 201 ✗, blanks only ✗ | As marked | P2 · Gap |
| TC-CLM-007 | **GSTIN.** Valid `33ABCDE1234F1Z5` ✓; empty ✓; lowercase ✗; 14 or 16 chars ✗; wrong 13th char ✗ | As marked | P2 · Gap |
| TC-CLM-008 | **Invoice number / date.** 60 chars ✓, 61 ✗; date optional | As marked | P3 · Gap |
| TC-CLM-009 | **Justification length.** 2000 ✓, 2001 ✗, whitespace only ✗ | As marked | P2 · Gap |
| TC-CLM-010 | **Unicode and special characters.** Vendor `Café ₹ 日本`, justification with `<script>` | Stored and shown escaped; no HTML executes | P2 · Gap |
| TC-CLM-011 | **Edit draft.** Change amount, Save | Updated; audited | P1 · ServiceFlowsIT `draftsCanBeEdited…` |
| TC-CLM-012 | **Edit locked after submit.** Open `/claims/{id}/edit` for a Submitted claim, POST an update | Refused | P1 · ServiceFlowsIT `draftsCanBeEdited…` |
| TC-CLM-013 | **Edit a Returned claim.** Edit and resubmit | Allowed | P2 · ServiceFlowsIT ~ |
| TC-CLM-014 | **Delete a draft never submitted.** Delete | Removed; documents removed from storage | P1 · ServiceFlowsIT ~ |
| TC-CLM-015 | **Delete refused.** Delete a Returned claim, or any non-draft | Refused | P1 · ServiceFlowsIT ~ |
| TC-CLM-016 | **Other team's claim.** Team B opens or edits Team A's claim by id | 404/403, no data in the response | P1 · AccessControlIT |
| TC-CLM-017 | **Stale form.** Open the edit form in two tabs; save in tab 1, then tab 2 | Tab 2 gets a "changed by someone else" error; no overwrite | P1 · Gap |
| TC-CLM-020 | **Live warnings: over budget.** Amount above the remaining budget | Non-blocking over-budget note with the remaining amount | P1 · ServiceFlowsIT `liveWarningsUseTheTeamsBudget` |
| TC-CLM-021 | **Live warnings: blocked category.** Accommodation | Blocking warning | P1 · ClaimWarningsTest |
| TC-CLM-022 | **Warnings at budget boundary.** Amount = remaining (no warning); remaining + 0.01 (warning) | Boundary | P2 · ClaimWarningsTest / BudgetSnapshotTest |
| TC-CLM-023 | **Warnings endpoint with garbage.** POST `/claims/warnings` with empty/negative values | No 500 | P2 · Gap |
| TC-CLM-030 | **My claims list / filters.** Team dashboard | Own claims only, newest first, correct status chips | P2 · Manual |

## 2.7 Documents (DOC)

Accepted types (by content): PDF, JPEG, PNG, HEIC/HEIF. Size ≤ 10 MB. Types: INVOICE, PAYMENT_PROOF.

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-DOC-001 | **Upload invoice (PDF).** Upload a valid PDF as Invoice | Listed with name, size, SHA-256 stored | P1 · ServiceFlowsIT ~ |
| TC-DOC-002 | **Upload proof (JPEG, PNG, HEIC).** One each | All accepted | P1 · FileTypeDetectorTest |
| TC-DOC-010 | **Renamed executable.** `evil.exe` renamed to `evil.pdf` | Rejected (content check) | P1 · FileTypeDetectorTest |
| TC-DOC-011 | **Valid PDF renamed `.txt`.** Upload | Accepted (type from content) | P1 · FileTypeDetectorTest |
| TC-DOC-012 | **HTML/SVG with fake `image/png` header.** Upload `<svg onload=…>` with content type `image/png` | Rejected | P1 · Gap |
| TC-DOC-013 | **Size boundary.** 10 MB exactly ✓; 10 MB + 1 byte ✗ ("Files must be 10 MB or smaller") | Boundary | P1 · Gap |
| TC-DOC-014 | **Empty file / 3 bytes.** Upload a 0-byte or 3-byte file | Rejected; no 500 | P2 · FileTypeDetectorTest |
| TC-DOC-015 | **Multi-file request over 25 MB.** Upload 3 × 9 MB at once | Friendly error | P2 · Gap |
| TC-DOC-016 | **Path traversal in file name.** File name `../../etc/passwd.pdf` | Stored under a random key; the original name sanitised on display and download | P2 · Gap |
| TC-DOC-017 | **Very long / unicode file name.** 300-char name, `रसीद.pdf` | Handled; download name valid | P3 · Gap |
| TC-DOC-020 | **Download as owner.** Click the document | Served with `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` | P1 · AccessControlIT |
| TC-DOC-021 | **Download as another team.** Team B calls `/documents/{id}` | 403/404 | P1 · AccessControlIT |
| TC-DOC-022 | **Download as staff / finance / COO.** Open | Allowed | P1 · Manual |
| TC-DOC-023 | **Non-existent id.** `/documents/999999` | 404, not 500 | P1 · Gap |
| TC-DOC-024 | **Preview.** PDF shown inline, images shown; HEIC offered as a download | As stated | P2 · Manual |
| TC-DOC-030 | **Delete in Draft.** Delete document | Removed from DB and storage | P1 · ServiceFlowsIT ~ |
| TC-DOC-031 | **Delete after submit.** Delete on a Submitted claim | Refused | P1 · Gap |
| TC-DOC-032 | **Delete another team's document.** Team B POSTs delete | 403/404 | P1 · AccessControlIT |
| TC-DOC-033 | **Upload after submit.** Upload to a Submitted claim | Refused | P2 · Gap |
| TC-DOC-034 | **Storage failure.** Make storage unwritable | Friendly error; no orphan row; nothing half-saved | P2 · Gap |
| TC-DOC-035 | **Rollback cleans storage.** Upload succeeds, transaction fails | Stored file removed | P3 · Gap |

## 2.8 Workflow (WF)

### 2.8.1 State-transition table

Rows are statuses, columns are actions. ✓ = legal (subject to permissions); ✗ = must throw "Cannot … because it is …". **Every ✗ cell is one test case** (`TC-WF-100` series: 7 × 7 = 49 cells: 12 legal ✓ and 37 illegal ✗).

| From \ Action | SUBMIT | VERIFY | RETURN | REJECT | APPROVE | MARK_PAID | REOPEN |
|---|---|---|---|---|---|---|---|
| DRAFT | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| SUBMITTED | ✗ | ✓ | ✓ | ✓ | ✓ (COO only) | ✗ | ✗ |
| VERIFIED | ✗ | ✗ | ✓ | ✓ | ✓ | ✗ | ✗ |
| RETURNED | ✓ (resubmit) | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| APPROVED | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ (COO) |
| PAID | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| REJECTED | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ (COO) |

Automated: `ClaimStateMachineTest` and `ServiceFlowsIT`.

### 2.8.2 Submit

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-WF-001 | **Submit complete draft.** Invoice + proof present, valid fields | Status Submitted; audit `SUBMITTED`; staff notified | P1 · HappyPathIT ~ |
| TC-WF-002 | **Missing invoice.** Only payment proof | Refused: "Upload the invoice before submitting. Pro-forma invoices are not accepted." | P1 · ClaimStateMachineTest |
| TC-WF-003 | **Missing payment proof.** Only invoice | Refused with the payment-proof message | P1 · ClaimStateMachineTest |
| TC-WF-004 | **Future expense date.** Date changed to tomorrow | Refused at submit | P1 · ClaimStateMachineTest |
| TC-WF-005 | **Non-reimbursable / Travel / Other short.** See TC-CAT-005..007 | Refused | P1 · ClaimStateMachineTest |
| TC-WF-006 | **Inactive category.** Category deactivated after draft creation | Refused | P1 · ServiceFlowsIT ~ |
| TC-WF-007 | **Only the team submits.** COO or staff submits | 403 ("Only members of the claim's team…") | P1 · ClaimStateMachineTest |
| TC-WF-008 | **Other team submits.** Team B submits Team A's draft | 403 | P1 · AccessControlIT |
| TC-WF-009 | **Resubmit after return.** Edit then submit | Audit `RESUBMITTED`; previous comment/reason retained in the history | P2 · ServiceFlowsIT ~ |
| TC-WF-010 | **Double click on Submit.** Two quick POSTs | One transition; the other gets a clear message | P2 · Gap |

### 2.8.3 Verify, return, reject

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-WF-020 | **Verify.** Staff with `CLAIM_VERIFY` verifies a Submitted claim | Verified; audit `VERIFIED`; approvers notified | P1 · HappyPathIT ~ |
| TC-WF-021 | **Verify without permission.** Staff without `CLAIM_VERIFY` | 403 | P1 · PermissionMatrixIT |
| TC-WF-022 | **Return needs a reason.** Return with empty/blank comment | Refused: "A reason is required…" | P1 · ClaimStateMachineTest |
| TC-WF-023 | **Return with reason.** `CLAIM_RETURN_REJECT` returns | Status Returned; team notified with the reason | P1 · ServiceFlowsIT ~ |
| TC-WF-024 | **Reject needs a reason.** Same as TC-WF-022 | Refused | P1 · ClaimStateMachineTest |
| TC-WF-025 | **Reject.** With reason | Rejected (terminal for the team); audit `REJECTED` | P1 · ServiceFlowsIT ~ |
| TC-WF-026 | **Return/reject from Verified.** Holder of `CLAIM_APPROVE` only | Allowed (approver's decision) | P1 · ClaimStateMachineTest |
| TC-WF-027 | **Return from Verified without either permission.** Holder of `CLAIM_VERIFY` only | 403 | P1 · ClaimStateMachineTest |
| TC-WF-028 | **Reason is trimmed.** Reason `"  x  "` | Stored `x` | P2 · Gap |
| TC-WF-029 | **Reason of 4,000+ chars.** Over the comment limit | Rejected cleanly | P3 · Gap |

### 2.8.4 Approve (full, partial, one-step)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-WF-030 | **Full approval.** Verified claim, approved amount empty | Approved at the claimed amount; audit `APPROVED` | P1 · HappyPathIT ~ |
| TC-WF-031 | **Partial approval requires reason.** Amount < claimed, no reason | Refused | P1 · ClaimStateMachineTest |
| TC-WF-032 | **Partial approval with reason.** Amount < claimed + reason | Approved at the lower amount; audit `PARTIALLY_APPROVED` | P1 · ServiceFlowsIT ~ |
| TC-WF-033 | **Approved amount boundaries.** 0 ✗, -1 ✗, 0.01 ✓ (partial), claimed ✓, claimed + 0.01 ✗ | As marked | P1 · ClaimStateMachineTest |
| TC-WF-034 | **Over budget.** Amount > remaining | Refused with the exact remaining amount and "Partially approve up to …" | P1 · PlatformIT `approvalsCannotOverspend…` |
| TC-WF-035 | **Amount = remaining.** Exactly equal | Approved | P1 · ServiceFlowsIT ~ |
| TC-WF-036 | **Budget already exhausted.** remaining = 0 | Refused, no "partially approve" hint | P1 · Gap |
| TC-WF-037 | **Four-eyes rule.** Staff with `CLAIM_VERIFY` + `CLAIM_APPROVE` verifies, then tries to approve | 403: "You verified this claim…" | P1 · StaffManagementIT `verifierCannotApprove…` |
| TC-WF-038 | **Another approver acts for the COO.** Second staff with `CLAIM_APPROVE` approves | Allowed; audit records `on behalf of` the COO | P1 · ServiceFlowsIT ~ |
| TC-WF-039 | **COO verifies and approves in one step.** COO approves a Submitted claim | Approved; audit `VERIFIED_AND_APPROVED`; the COO is the verifier | P1 · StaffManagementIT `cooCanVerifyAndApprove…` |
| TC-WF-040 | **Two approvers, same claim.** Both open the claim at version N; A approves; B approves | B gets a concurrency error; one audit entry | P1 · PlatformIT `twoApproversCannotDoubleAct…` |
| TC-WF-041 | **Non-COO approves from Submitted.** Staff with `CLAIM_APPROVE` on a Submitted claim | 403 "Only the COO can verify and approve in one step" | P1 · ClaimStateMachineTest |
| TC-WF-042 | **Stale version.** Send an old `version` | Rejected with a refresh message | P1 · ClaimStateMachineTest |
| TC-WF-043 | **Approved amount in other formats.** `1,000`, `1000.5`, `1000.555` | Normalised to 2 decimals or rejected; never a 500 | P2 · MoneyTest (partial) |

### 2.8.5 Reopen

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-WF-050 | **COO reopens Rejected.** With reason | Submitted; verification/approval cleared; audit `REOPENED` | P1 · ServiceFlowsIT ~ |
| TC-WF-051 | **COO reopens Approved (unpaid).** With reason | Submitted; approved amount cleared | P1 · ServiceFlowsIT ~ |
| TC-WF-052 | **Cannot reopen Paid.** Reopen a Paid claim | Illegal-transition message | P1 · ClaimStateMachineTest |
| TC-WF-053 | **Non-COO reopens.** Any other role | 403 | P1 · ClaimStateMachineTest |
| TC-WF-054 | **Reopen without a reason.** Empty | Refused | P1 · ClaimStateMachineTest |
| TC-WF-055 | **After reopening, a different verifier can act, including the earlier one.** Verify again | Allowed | P2 · Gap |

### 2.8.6 Order of checks

| ID | Test | Expected result | P · Auto |
|---|---|---|---|
| TC-WF-060 | An illegal state returns the state message, not a 403 (for example, a team member verifies a Paid claim) | "Cannot verify claim … because it is paid." | P1 · ClaimStateMachineTest |
| TC-WF-061 | Unknown claim id | 404 | P1 · AccessControlIT |
| TC-WF-062 | Unknown action in URL `/claims/1/actions/EXPLODE` | 400/404, not 500 | P1 · Gap |

## 2.9 Priority (PRI)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-PRI-001 | **Flag a claim.** `CLAIM_FLAG_PRIORITY` flags with a reason | Appears at the top of the queue; audited | P1 · ServiceFlowsIT ~ |
| TC-PRI-002 | **Unflag.** Remove the flag | Back to the normal order | P1 · ServiceFlowsIT ~ |
| TC-PRI-003 | **Without permission.** Staff lacking the permission POSTs `/claims/{id}/priority` | 403 | P1 · PermissionMatrixIT |
| TC-PRI-004 | **Flag a team.** Staff flags a team | All the team's claims are lifted | P1 · ServiceFlowsIT `teamPriorityLiftsAllItsClaims` |
| TC-PRI-005 | **Team requests priority.** Team member requests with a reason | Staff notified | P1 · ServiceFlowsIT `teamRequestsPriority…` |
| TC-PRI-006 | **Staff accepts/declines.** Accept → flagged. Decline → request cleared, team notified | As stated | P1 · ServiceFlowsIT ~ |
| TC-PRI-007 | **Request on a terminal claim.** Request on a Paid claim | Refused | P2 · Gap |
| TC-PRI-008 | **Queue order.** Priority first, then oldest in the current stage | Ordering correct, ties stable | P2 · ClaimQueueOrderingTest |

## 2.10 Staff queue and COO approvals (COO)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-COO-001 | **Staff queue contents.** Open `/staff/queue` | Submitted claims (plus Verified for approvers), priority first | P1 · HappyPathIT ~ |
| TC-COO-002 | **Queue access.** Finance user, team member | 403 | P1 · PermissionMatrixIT |
| TC-COO-003 | **Filters.** Filter by team/status/age | Result set matches | P2 · Gap |
| TC-COO-010 | **Approvals screen.** COO opens `/coo/approvals` | Verified claims, priority at the top; tap to expand | P1 · Manual |
| TC-COO-011 | **Single approve / return / reject from the screen.** `/coo/approvals/{id}/{decision}` | Same results as TC-WF-030 / 023 / 025 | P1 · HappyPathIT ~ |
| TC-COO-012 | **Bulk approve.** Select 3 valid claims → Approve selected | All approved | P1 · ServiceFlowsIT `bulkApprove…` |
| TC-COO-013 | **Bulk with failures.** Select one valid, one over budget, one already-approved | Valid approved; the others reported with reasons; no partial transaction for the failed ones | P1 · ServiceFlowsIT `bulkApprove…` |
| TC-COO-014 | **Bulk, none selected.** Click with nothing selected | Friendly message | P1 · Gap |
| TC-COO-015 | **Bulk, tampered ids.** Include ids from claims not in Verified | Reported as failed | P1 · Gap |
| TC-COO-016 | **Bulk obeys four-eyes.** Approver who verified a claim includes it | That claim fails, others pass | P1 · Gap |
| TC-COO-017 | **Mobile layout.** 360 px viewport | Usable without horizontal scroll; tap targets ≥ 44 px | P2 · Manual |
| TC-COO-018 | **HTMX partial update.** Approve a row | Row updates without a full reload; the flash message appears; CSRF header sent | P2 · Manual |
| TC-COO-019 | **Access.** Staff without `CLAIM_APPROVE` opens approvals | 403 | P1 · PermissionMatrixIT |

## 2.11 Payments (PAY)

Form: paid-on date (≤ today), mode, UTR (required, ≤ 40, `[A-Za-z0-9-]+`).

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-PAY-001 | **Queue.** Finance opens `/finance/payments` | Approved claims with approved amounts | P1 · HappyPathIT ~ |
| TC-PAY-002 | **Mark as paid.** Valid form | Paid; payment row; audit `PAID`; team notified | P1 · HappyPathIT ~ |
| TC-PAY-003 | **UTR missing.** Empty / spaces | "The transaction reference / UTR is required" | P1 · Gap |
| TC-PAY-004 | **UTR boundaries.** 40 chars ✓; 41 ✗; `AB-12` ✓; `AB 12`, `AB_12`, `AB#12` ✗ | As marked | P1 · Gap |
| TC-PAY-005 | **Date boundaries.** Today ✓, tomorrow ✗, empty ✗ | As marked | P1 · Gap |
| TC-PAY-006 | **Mode.** Each `PaymentMode` accepted; an invalid value rejected | As marked | P1 · Gap |
| TC-PAY-007 | **Pay a non-approved claim.** Pay Verified / Paid claim | Illegal-transition message | P1 · ClaimStateMachineTest |
| TC-PAY-008 | **Double payment.** Two finance users submit at once | One payment only | P1 · Gap |
| TC-PAY-009 | **Without `PAYMENT_RECORD`.** Staff or COO-delegate without it | 403 | P1 · PermissionMatrixIT |
| TC-PAY-010 | **Payment amount.** Equals the approved amount, not the claimed one, for partial approvals | Correct | P2 · ServiceFlowsIT ~ |
| TC-PAY-011 | **Budget "spent" after payment.** Dashboard | Spent/remaining match the formula in `BudgetSnapshot` | P2 · BudgetSnapshotTest |
| TC-PAY-012 | **Paid claim is read-only.** No edit/delete/upload buttons | As stated | P2 · Manual |

## 2.12 Comments (CMT)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-CMT-001 | **Team comments.** Post a comment | Shown in the thread; staff notified; author not notified | P1 · ServiceFlowsIT `commentThreadNotifiesTheOtherSide` |
| TC-CMT-002 | **Staff comment.** Post a comment | Team notified | P1 · ServiceFlowsIT ~ |
| TC-CMT-003 | **Empty / whitespace.** Post | Rejected | P2 · Gap |
| TC-CMT-004 | **4000 / 4001 chars.** Boundary | Accept / reject | P2 · Gap |
| TC-CMT-005 | **HTML in comment.** `<img src=x onerror=alert(1)>` | Rendered as text | P1 · Gap |
| TC-CMT-006 | **Other team's claim.** Team B posts | 403/404 | P1 · AccessControlIT |
| TC-CMT-007 | **Comment on a Paid claim.** Post | Allowed or refused as designed (document) | P3 · Gap |

## 2.13 Notifications and email (NTF)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-NTF-001 | **Submission.** Team submits | Holders of `CLAIM_VERIFY` get an in-app notification and an email (COO only if nobody else can verify) | P1 · ServiceFlowsIT ~ |
| TC-NTF-002 | **Verified.** Staff verifies | Holders of `CLAIM_APPROVE`, including the COO, notified | P1 · ServiceFlowsIT ~ |
| TC-NTF-003 | **Approved / returned / rejected / paid.** Each action | Team notified | P1 · ServiceFlowsIT ~ |
| TC-NTF-004 | **No self-notification.** Actor is also a recipient | The actor gets none | P1 · ServiceFlowsIT ~ |
| TC-NTF-005 | **Badge.** `/notifications/badge` | Unread count correct; updates after read | P2 · ServiceFlowsIT ~ |
| TC-NTF-006 | **Open.** `/notifications/{id}/open` | Marked read and redirected to the claim | P2 · ServiceFlowsIT ~ |
| TC-NTF-007 | **Open another user's notification.** Use someone else's id | 403/404; not marked read | P1 · Gap |
| TC-NTF-008 | **Read all.** Mark all read | Badge 0 | P2 · ServiceFlowsIT ~ |
| TC-NTF-010 | **SMTP down.** Stop MailHog, then act | Action succeeds; the in-app notification exists; the error is logged only | P1 · Gap |
| TC-NTF-011 | **Email after commit.** A rolled-back action | No email | P2 · Gap |
| TC-NTF-012 | **Email content.** Check in MailHog: subject, code, link uses `APP_BASE_URL`, no sensitive data | Correct | P3 · Manual |

## 2.14 Audit log (AUD)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-AUD-001 | **Every transition audited.** Run the happy path | One entry per transition: actor, permission used, from → to, comment | P1 · HappyPathIT ~ |
| TC-AUD-002 | **Access.** Only `AUDIT_LOG_VIEW` holders open `/audit` | Others 403 | P1 · PermissionMatrixIT |
| TC-AUD-003 | **Filters and paging.** Filter by entity/action/actor | Correct | P2 · Manual |
| TC-AUD-004 | **Append-only.** `UPDATE`/`DELETE` on `audit_log` via SQL | Rejected by the trigger | P1 · PlatformIT `auditLogIsAppendOnly` |
| TC-AUD-005 | **Failed actions aren't logged as success.** An illegal transition | No entry written | P2 · Gap |
| TC-AUD-006 | **Times in IST.** Display | `dd MMM yyyy`, IST | P2 · TimeFormatsTest |

## 2.15 Team dashboard (DASH)

| ID | Test (steps) | Expected result | P · Auto |
|---|---|---|---|
| TC-DASH-001 | **Budget meter.** Team user opens `/team` | Budget, spent, pending, remaining correct, ₹ in Indian format (`₹1,25,000.00`) | P1 · ServiceFlowsIT `teamDashboardShowsBudgetMeter` |
| TC-DASH-002 | **Meter states.** Under / near / over budget | Colour and text match the thresholds | P2 · BudgetSnapshotTest |
| TC-DASH-003 | **Role routing.** Staff opens `/team` | 403 (`hasRole('TEAM_MEMBER')`) | P1 · PermissionMatrixIT |
| TC-DASH-004 | **Team with no claims.** New team | Empty state, no errors | P2 · Manual |
| TC-DASH-005 | **Status timeline.** Open a claim | Timeline matches the audit | P2 · Manual |

## 2.16 End-to-end scenarios (E2E)

| ID | P | Scenario |
|---|---|---|
| TC-E2E-001 | 1 | **Happy path.** Team creates → uploads invoice + proof → submits → staff verifies → COO approves → finance pays → team sees Paid + notifications + emails. |
| TC-E2E-002 | 1 | **Return loop.** Submit → staff returns with a reason → team edits, adds a document → resubmits → verified → approved → paid. Audit shows `RESUBMITTED`. |
| TC-E2E-003 | 1 | **Partial approval.** COO approves 60% with a reason → finance pays the lower amount → budget "spent" reflects the lower amount. |
| TC-E2E-004 | 1 | **Rejection and reopen.** Reject → COO reopens with a reason → verify again → approve → pay. |
| TC-E2E-005 | 1 | **Priority.** The team requests priority → staff accepts → the claim rises in both queues → COO approves it first. |
| TC-E2E-006 | 1 | **Budget exhaustion.** Pay and approve until the remaining budget is under the next claim → approval refused with the remaining amount → partial approval up to it works. |
| TC-E2E-007 | 1 | **Staff on-boarding.** COO invites → staff activates → COO grants `CLAIM_VERIFY` → staff verifies at once → COO revokes → staff gets 403 at once. |
| TC-E2E-008 | 1 | **COO hand-over.** Hand over → old COO loses staff-management and gains the staff bundle → new COO can approve → hand back. |
| TC-E2E-009 | 2 | **New cohort to first payment.** Create cohort → team → member → member signs in (invited) → claim → paid. |
| TC-E2E-010 | 2 | **Category lifecycle.** Add a category → team uses it → deactivate it → old claims still display; new claims can't pick it. |
