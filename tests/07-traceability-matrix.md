# 7. Traceability Matrix

Maps each feature to its requirement source, its manual test cases and the automated tests that cover it.

## 7.1 Feature coverage

| Feature | Requirement source | Black-box | White-box | Security / NF | Automated tests (src/test/java) |
|---|---|---|---|---|---|
| Login, rate limit, session | README *Key design points*; ASSUMPTIONS #9, #10 | TC-AUTH-001..014 | WB-LOG-001..007 | TC-SEC-001..010 | `LoginAttemptServiceTest`, `StaffManagementIT` (`loginIsRateLimited`, `deactivatedUsers…`), `AccessControlIT` (`anonymousUsers…`) |
| Invitations | ASSUMPTIONS #5, #6 | TC-INV-001..012 | WB-USR-001, WB-TOK-001 | TC-SEC-008 | `StaffManagementIT` (`invitedStaff…`) |
| Staff and permissions | README *Permissions, not roles*; ASSUMPTIONS #5–#9 | TC-USR-001..045 | WB-PER-001..011 | TC-SEC-011..019 | `PermissionServiceTest`, `PermissionMatrixIT`, `StaffManagementIT` |
| COO hand-over | ASSUMPTIONS #6 | TC-USR-040..044, TC-E2E-008 | WB-USR-001 | – | `StaffManagementIT` (`cooHandOver…`, `cooCannotBeHandedToATeamMember`) |
| Cohorts, teams, members, budgets | ASSUMPTIONS #4, #23, #28; README *Common admin tasks* | TC-TEAM-001..025 | WB-TEAM-001, WB-BUD-001..004 | TC-SEC-025, 026 | `ServiceFlowsIT` (`staffManageTeams…`), `BudgetSnapshotTest` |
| Categories | ASSUMPTIONS #20, #21 | TC-CAT-001..008 | WB-CAT-001, WB-SM-020..025 | – | `ServiceFlowsIT`, `ClaimStateMachineTest`, `ClaimWarningsTest` |
| Claim create/edit/delete | ASSUMPTIONS #18, #19, #22 | TC-CLM-001..030 | WB-SM-026 | TC-SEC-030, 031, 065, 066 | `ServiceFlowsIT` (`draftsCanBe…`, `liveWarnings…`), `PlatformIT` (`claimCodes…`) |
| Documents | README *Uploads*; ASSUMPTIONS #27 | TC-DOC-001..035 | WB-FTD-001..009, WB-DOC-001..006, WB-STO-001..002 | TC-SEC-032, 050..056 | `FileTypeDetectorTest`, `AccessControlIT` |
| Workflow and state machine | README *One state machine*; ASSUMPTIONS #11–#17 | TC-WF-001..062, TC-WF-100 series | WB-SM-001..035, WB-ACT-001..004 | TC-SEC-060..069 | `ClaimStateMachineTest`, `StaffManagementIT`, `PlatformIT`, `HappyPathIT` |
| Priority | ASSUMPTIONS #26 | TC-PRI-001..008, TC-E2E-005 | WB-PRI-001, WB-QUE-001..005 | – | `ServiceFlowsIT` (`teamRequestsPriority…`, `teamPriorityLiftsAllItsClaims`), `ClaimQueueOrderingTest` |
| Staff queue, COO approvals, bulk | README *Try the full flow* | TC-COO-001..019 | WB-QSV-001, WB-BLK-001 | – | `ServiceFlowsIT` (`bulkApprove…`), `HappyPathIT` |
| Payments | Phase 1 scope | TC-PAY-001..012 | WB-PAY-001 | TC-SEC-064 | `HappyPathIT`, `ClaimStateMachineTest` (`markPaidRequiresPaymentRecord`) |
| Comments | ASSUMPTIONS #24 | TC-CMT-001..007 | WB-CMT-001 | TC-SEC-030 | `ServiceFlowsIT` (`commentThread…`) |
| Notifications and email | ASSUMPTIONS #3, #24, #25 | TC-NTF-001..012 | WB-NTF-001..003 | NF-REC-005 | `ServiceFlowsIT` (`notificationsCanBeOpened…`) |
| Audit log | README *Audit log is append-only* | TC-AUD-001..006 | WB-AUD-001, WB-DB-001 | TC-SEC-070..074 | `PlatformIT` (`auditLogIsAppendOnly`), `StaffManagementIT` (`grantsAndRevokesAreAudited`) |
| Team dashboard / budget meter | README | TC-DASH-001..005 | WB-BUD-001..004 | – | `HappyPathIT` (`teamDashboardShowsBudgetMeter`), `BudgetSnapshotTest` |
| Money / time formats | README *Timezones* | NF-LOC-001..004 | WB-MON-001..003 | – | `MoneyTest`, `TimeFormatsTest` |
| Concurrency | README *Concurrency* | – | – | NF-CON-001..009 | `PlatformIT` |
| Database rules | README, V1/V2 migrations | – | WB-DB-001..008 | – | `PlatformIT`, `PermissionCatalogCheck` at start-up |

## 7.2 Business rules → tests

| Rule (ASSUMPTIONS / README) | Test cases |
|---|---|
| Permissions, not roles; the COO holds everything | TC-USR-014, TC-SEC-011, WB-PER-001 |
| `STAFF_MANAGE` cannot be granted | TC-USR-012, TC-SEC-012, WB-PER-004, WB-DB-002 |
| Changes apply on the next request | TC-USR-020..023, TC-SEC-017 |
| Exactly one COO; hand-over only | TC-USR-032, 040..042 |
| One team per student | TC-TEAM-021, WB-DB-003 |
| Only the team submits | TC-WF-007, 008 |
| Partial approval needs a reason | TC-WF-031, 032, WB-SM-013 |
| COO verify+approve in one step | TC-WF-039, 041 |
| Return/reject from Verified | TC-WF-026, 027, WB-SM-006 |
| No overspending of the budget | TC-WF-034..036, NF-CON-002, WB-SM-033..035 |
| Four-eyes | TC-WF-037, 038, TC-COO-016, WB-SM-008 |
| Reopen: REJECTED/APPROVED only; reason | TC-WF-050..055 |
| State is checked before permission | TC-WF-060, WB-SM-003 |
| Claim code `NRM-<year>-<5 digits>` | TC-CLM-002 |
| "Other" ≥ 30 characters | TC-CAT-007, WB-SM-025 |
| Travel is blocked until pre-approvals ship | TC-CAT-006, WB-SM-023 |
| Delete only never-submitted drafts | TC-CLM-014, 015 |
| Users are not notified of their own actions | TC-NTF-004, WB-NTF-001 |
| Files typed by content, ≤ 10 MB | TC-DOC-010..014, WB-FTD, WB-DOC-001 |
| Documents only via an authorised endpoint | TC-DOC-020..023, TC-SEC-054 |
| Email is best-effort | TC-NTF-010, 011, NF-REC-005 |
| Login lock 5/15 min | TC-AUTH-004..007, WB-LOG-001..005 |
| Grant expiry: last day inclusive, midnight IST | TC-USR-022, 023, WB-PER-003 |
| Audit is append-only | TC-AUD-004, WB-DB-001 |
| Timezones: IST display, UTC storage | TC-AUD-006, NF-LOC-002, 003, WB-SM-026 |

## 7.3 Automation gaps

Cases still marked **Gap** in [02](02-black-box-test-cases.md) (61 at the time of writing). Suggested order:

**Do first (P1, money and security)**

1. Payment form validation and double payment: TC-PAY-003..006, 008.
2. Bulk approval edge cases: TC-COO-014..016.
3. Claim form field validation (one parameterised MockMvc test for TC-CLM-003, 004, 006, 007, 009).
4. Upload boundaries and disguised files: TC-DOC-012, 013, 016, 023, 031, 033.
5. Stale form on edit: TC-CLM-017; unknown workflow action: TC-WF-062.
6. SMTP down: TC-NTF-010.
7. Another user's notification: TC-NTF-007.
8. Budget exhausted with no hint: TC-WF-036.

**Then (P2)**

9. Invitation errors: TC-INV-004, 005, 007, 008.
10. Team budget boundaries: TC-TEAM-013, 014, 016.
11. Comment limits and XSS: TC-CMT-003..005.
12. Stored XSS sweep: TC-SEC-030 plus a build-time check that no template uses `th:utext` on user data.
13. Security headers and the session-fixation check: TC-SEC-004, 043.

**Counts by status (black-box table rows):** 229 cases; 61 Gap; 17 Manual; the rest map to an automated class (some marked ~ and to be confirmed). Re-count with:

```bash
grep -E '^\| TC-' tests/02-black-box-test-cases.md | grep -cE '\| Gap \|$'
```

## 7.4 Keeping this current

- A new feature adds a row to 7.1 and cases to 02 (and 03 if it has logic).
- A new `*Test` or `*IT` replaces a **Gap**: update the Auto cell in 02 and remove it from 7.3.
- A bug fix adds a regression case with the defect id in its title.
