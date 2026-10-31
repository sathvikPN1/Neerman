# 3. White-Box Test Cases

These cases come from the source code. Each lists the branches or paths to cover. **Existing** names the automated test that already does it; **Add** is a recommended new test.

Coverage targets: statement ≥ 90% and branch ≥ 80% in `claim`, `user`, `payment`, `document`, `team`, `config`, `common`. Baseline service line coverage: about 93% (JaCoCo).

---

## 3.1 `ClaimStateMachine` (claim/ClaimStateMachine.java)

### `transition()` control flow

```
find claim ──missing──▶ NotFoundException                                   [WB-SM-001]
version mismatch ────▶ ConcurrentUpdateException                            [WB-SM-002]
action not allowed from status ──▶ IllegalTransitionException               [WB-SM-003]
authorize() ──▶ AccessDeniedException                                        [WB-SM-004..]
reasonRequired && no comment ──▶ BusinessRuleException                       [WB-SM-010]
switch(action): SUBMIT | VERIFY | RETURN | REJECT | APPROVE | MARK_PAID | REOPEN
saveAndFlush ──ObjectOptimisticLockingFailure──▶ ConcurrentUpdateException   [WB-SM-011]
audit.record → notifier.onTransition
```

| ID | Target | Inputs / path | Expected | Existing / Add |
|---|---|---|---|---|
| WB-SM-001 | Claim lookup | Unknown id | `NotFoundException` | Add (unit) |
| WB-SM-002 | Version check, both sides | `expectedVersion` null → skipped; equal → proceeds; different → exception | Three paths | `staleVersionIsRejected` (+ Add null/equal) |
| WB-SM-003 | `isAllowedFrom` | All 49 (status, action) pairs | 12 legal pairs succeed (see `ClaimAction`); the other 37 throw `IllegalTransitionException` | `everyLegalTransitionSucceedsAndEveryIllegalOneIsRejected`, `legalTransitionTableMatchesTheSpecification` |
| WB-SM-004 | `authorize` SUBMIT | Member of the team; non-member; COO | Only a member passes | `onlyMembersOfTheClaimsTeamCanSubmit` |
| WB-SM-005 | `authorize` VERIFY | With / without `CLAIM_VERIFY` | Pass / deny | `verifyRequiresClaimVerify` |
| WB-SM-006 | `authorize` RETURN/REJECT, branch 1: status = VERIFIED and no `CLAIM_RETURN_REJECT` → needs `CLAIM_APPROVE` | 4 combinations of {VERIFIED, SUBMITTED} × {has / lacks return-reject} × {has / lacks approve} | Condition coverage on `&&` | `returnFromSubmittedRequiresClaimReturnReject`, `approverMayReturnOrRejectAVerifiedClaim` (+ Add lacks both) |
| WB-SM-007 | `authorize` APPROVE, status = SUBMITTED | COO ok; non-COO denied | Two paths | `onlyTheCooCanVerifyAndApproveInOneStep` |
| WB-SM-008 | `authorize` APPROVE, status = VERIFIED, four-eyes | {non-COO, id = verifier} deny; {non-COO, other id} pass; {COO, id = verifier} pass | Condition coverage for `!isCoo && id.equals(verifiedBy)` | `verifierCannotApproveTheirOwnVerifiedClaim`, `cooMayApproveAClaimTheyVerified` |
| WB-SM-009 | `authorize` MARK_PAID / REOPEN | Permission present/absent; COO / non-COO | Pass / deny | `markPaidRequiresPaymentRecord`, `onlyTheCooCanReopen` |
| WB-SM-010 | `reasonRequired` | RETURN, REJECT, REOPEN with null, `""`, `"  "`, `"x"` | The first three throw | `returnAndRejectAndReopenRequireAReason` |
| WB-SM-011 | Optimistic lock at flush | Mock repo throws `ObjectOptimisticLockingFailureException` | Converted | `optimisticLockFailureOnFlushBecomesConcurrentUpdate` |
| WB-SM-012 | APPROVE, `from == SUBMITTED` | Sets verifier; audit action prefix `VERIFIED_AND_` | True / false branches | `onlyTheCooCanVerifyAndApproveInOneStep` (+ Add: audit action string asserted) |
| WB-SM-013 | APPROVE, partial vs full | amount < claimed (reason needed) / = claimed | `PARTIALLY_APPROVED` / `APPROVED` | `partialApprovalNeedsAReasonAndSetsTheApprovedAmount`, `fullApprovalUsesTheClaimedAmount` |
| WB-SM-014 | APPROVE by non-COO sets `onBehalfOf` = COO id; by COO leaves null | Two paths; plus no active COO → null | 3 paths | `delegatedApprovalIsLoggedOnBehalfOfTheCoo` (+ Add no-COO) |
| WB-SM-015 | REOPEN | Uses `claim.getSubmittedBy()`; clears approval | State after reopening | `reopenClearsApproval` |
| WB-SM-016 | SUBMIT from RETURNED vs DRAFT | Audit action `RESUBMITTED` / `SUBMITTED` | Two values | `resubmittingAReturnedClaimClearsThePreviousDecision` |
| WB-SM-017 | Comment trimming | `"  hi "` → `"hi"`; null stays null; comment placed in audit detail only if non-null | | Add |
| WB-SM-018 | `canPerform` | Returns false for illegal state; false for denied; true otherwise; never throws | 3 paths | `canPerformReflectsStateAndPermissions` |
| WB-SM-019 | `verb()` | One assertion per enum constant (switch exhaustiveness) | 7 strings | Add |

### `validateSubmission()` branches

| ID | Branch | Input | Expected | Existing / Add |
|---|---|---|---|---|
| WB-SM-020 | Category missing | `categoryId` not found | "Choose a category." | Add |
| WB-SM-021 | Inactive | `active=false` | "no longer available" | Add |
| WB-SM-022 | Not allowed | `allowed=false` | Message includes the category description | `submitIsBlockedForDisallowedCategories` |
| WB-SM-023 | Pre-approval | required & no id / required & id / not required | Throw / pass / pass | `submitIsBlockedWithoutPreApprovalWhenRequired` |
| WB-SM-024 | Justification null / empty / blank | Three inputs | "Explain how…" | Add (null, blank) |
| WB-SM-025 | Detailed justification | Required: 29, 30 chars; not required: 1 char | Throw, pass, pass | `otherCategoryNeedsADetailedJustification` (+ Add 29/30 boundary) |
| WB-SM-026 | Future date | `expenseDate` null / today / tomorrow (IST clock at 18:29 and 18:31 UTC) | Pass / pass / throw; the day-change edge in IST | Add |
| WB-SM-027 | Documents | invoice ∧ proof; invoice ∧ ¬proof; ¬invoice ∧ proof; neither | Three throws and one pass; the invoice message comes first | `submitNeedsInvoiceAndPaymentProof` |

### `approvedAmount()` / `checkBudget()`

| ID | Branch | Input | Expected | Existing / Add |
|---|---|---|---|---|
| WB-SM-030 | null requested amount | → claimed amount | | `fullApprovalUsesTheClaimedAmount` |
| WB-SM-031 | `signum() <= 0` | 0, -1, 0.001 (normalised to 0.00?) | Throw | `approvedAmountMustBePositiveAndNotAboveClaimed` (+ Add 0.001) |
| WB-SM-032 | `> claimed` | claimed + 0.01 | Throw | same |
| WB-SM-033 | `exceedsRemaining` | remaining 100: 99.99, 100.00, 100.01 | Pass, pass, throw | `approvalCannotExceedTheRemainingBudgetButPartialUpToItIsAllowed` |
| WB-SM-034 | Message: remaining > 0 gets the hint; remaining ≤ 0 has none; negative remaining shown as ₹0 | | 3 strings | Add |
| WB-SM-035 | Lock called before the snapshot | Verify the `teams.lockById` call order (Mockito `InOrder`) | Locks first | Add |

## 3.2 `ClaimAction`, `ClaimStatus`

| ID | Target | Expected | Existing / Add |
|---|---|---|---|
| WB-ACT-001 | `from()` returns a defensive copy | Mutating it doesn't alter the enum | Add |
| WB-ACT-002 | `reasonRequired` is true for RETURN, REJECT, REOPEN only | | Add |
| WB-ACT-003 | `isEditableByTeam` = DRAFT, RETURNED; `isTerminal` = PAID, REJECTED | Table check | Add |
| WB-ACT-004 | Every `ClaimStatus` is reachable from DRAFT (graph) and PAID has no outgoing edge | | Add |

## 3.3 `ClaimWarnings`

| ID | Branch | Expected | Existing |
|---|---|---|---|
| WB-WRN-001 | Category blocks, with the description | Blocking | `disallowedCategoryBlocksAndExplainsTheRule` |
| WB-WRN-002 | Missing pre-approval | Blocking | `missingPreApprovalBlocks` |
| WB-WRN-003 | Recommended pre-approval, justification advised | Non-blocking | `recommendedPreApprovalAndJustificationAreNonBlockingNotes` |
| WB-WRN-004 | Over budget: amount null, ≤ remaining, > remaining | No warning / none / warning | `overBudgetIsAWarningNotABlock` (+ Add null amount) |

## 3.4 `ClaimQueueOrdering`

| ID | Case | Expected | Existing |
|---|---|---|---|
| WB-QUE-001 | Priority before non-priority | | `priorityFirstThenOldestFirst` |
| WB-QUE-002 | Team priority counts as claim priority | Add | Add |
| WB-QUE-003 | Equal age → ordered by id | | `ties_areBrokenById` |
| WB-QUE-004 | Empty list, single item | No exception | Add |
| WB-QUE-005 | Age is taken from the time in the current stage, not from creation | Add | Add |

## 3.5 `BudgetSnapshot` and `Money`

| ID | Case | Expected | Existing |
|---|---|---|---|
| WB-BUD-001 | `remaining = budget − (approved + paid)` | | `remainingIsBudgetMinusApprovedAndPaid` |
| WB-BUD-002 | `exceedsRemaining` at − 0.01, 0, + 0.01 | | `exceedsRemaining` |
| WB-BUD-003 | Nulls become zero; scale is 2 | | `nullsAreZeroAndScaleIsTwo` |
| WB-BUD-004 | Meter percentages are capped at 100 and floored at 0 when budget = 0 | | `meterPercentagesNeverExceedHundred` |
| WB-MON-001 | `formatInr`: 0, 999.99, 1,000.00, 1,00,000.00, 12,34,56,789.50, null → "–", negative | | `formatsWithIndianGrouping`, `nullIsADash` |
| WB-MON-002 | `normalise`: HALF_UP vs HALF_EVEN at x.xx5 | Add | Add |
| WB-MON-003 | `percent` with total 0 | 0, not an exception | `percentRoundsAndHandlesZeroTotals` |

## 3.6 `FileTypeDetector`

| ID | Case | Expected | Existing |
|---|---|---|---|
| WB-FTD-001 | `null`; 0–3 bytes | Empty (the `< 4` guard) | `rejectsEverythingElseRegardlessOfName` |
| WB-FTD-002 | `%PDF-` prefix; `%PDF` without the dash | PDF; empty | `detectsByMagicBytes` (+ Add) |
| WB-FTD-003 | JPEG: `FF D8 FF`; `FF D8 00` | JPEG; empty | Add boundary |
| WB-FTD-004 | PNG 8-byte signature; 7-byte prefix | PNG; empty | Add boundary |
| WB-FTD-005 | HEIC: length 11 / 12; each brand in `HEIC_BRANDS`; `ftyp` with brand `mp42` | Empty; HEIC; HEIC; empty | `detectsByMagicBytes` (+ Add) |
| WB-FTD-006 | `startsWith` with a shorter array than the prefix | false | Add |
| WB-FTD-007 | `isImage` | JPEG/PNG true; PDF/HEIC/null false | Add |
| WB-FTD-008 | `sanitiseFilename` (path separators, `..`, control chars, empty, long) | | `sanitisesFilenames` |
| WB-FTD-009 | `sha256` is lowercase 64-hex | | `sha256IsHex` |

## 3.7 `DocumentService`

| ID | Path | Expected | Add |
|---|---|---|---|
| WB-DOC-001 | length > `MAX_BYTES` (boundary: = and +1) | `BusinessRuleException` | Yes |
| WB-DOC-002 | Type not detected | Rejected, nothing stored | Yes |
| WB-DOC-003 | Claim not editable (status ∉ {DRAFT, RETURNED}) | Refused | Yes |
| WB-DOC-004 | Not a member of the claim's team | Access denied | Yes |
| WB-DOC-005 | DB failure after storing the file | The compensating delete runs (transaction synchronisation) | Yes |
| WB-DOC-006 | Delete: row removed and then the file; storage failure on delete is logged, not thrown | | Yes |

## 3.8 `PermissionService` and `AppUserPrincipal`

| ID | Branch | Expected | Existing |
|---|---|---|---|
| WB-PER-001 | COO has all, including `STAFF_MANAGE` | | `cooAlwaysHoldsEveryPermissionIncludingStaffManage` |
| WB-PER-002 | Inactive user has none | | `inactiveUsersHaveNoPermissions` |
| WB-PER-003 | Effective = active grants only; expiry boundary: `expiresAt` = now → inactive; now − 1 ns → inactive; now + 1 ns → active | | `effectivePermissionsComeFromActiveGrantsOnly`, `userPermissionExpiryCheck` (+ Add exact boundary) |
| WB-PER-004 | `STAFF_MANAGE` can't be granted (service) | | `staffManageCanNeverBeGranted` |
| WB-PER-005 | COO's grants can't be changed | | `cooPermissionsCannotBeChanged` |
| WB-PER-006 | Team members can't get back-office grants | | `teamMembersCannotGetStaffPermissions` |
| WB-PER-007 | Expiry must be in the future | | `expiryMustBeInTheFuture` |
| WB-PER-008 | Grant / revoke / update expiry, each audited | | `setPermissionsGrantsRevokesAndUpdatesWithAudit` |
| WB-PER-009 | Cleanup job removes expired, audits as system | | `expiredGrantsAreRemovedAndLoggedAsSystemAction` |
| WB-PER-010 | Role defaults can't contain `STAFF_MANAGE` or apply to the COO | | `roleDefaultsCannotContainStaffManageOrApplyToCoo` |
| WB-PER-011 | `PermissionCatalogCheck`: enum vs the table drift → startup fails | | Add (context-load test with a missing row) |

## 3.9 `LoginAttemptService` and filters

| ID | Branch | Expected | Existing |
|---|---|---|---|
| WB-LOG-001 | N−1 failures: not blocked; N: blocked | | `locksAfterMaxFailuresAndUnlocksAfterTheLockPeriod` |
| WB-LOG-002 | `lockedUntil` boundary: now = lockedUntil | Unblocked | Add |
| WB-LOG-003 | `recordSuccess` clears | | `successResetsTheCounter` |
| WB-LOG-004 | Null / blank / mixed-case username | Normalised; no NPE | Add |
| WB-LOG-005 | Failures after lock expiry start a new count | | Add |
| WB-LOG-006 | `LoginRateLimitFilter`: blocks only POST `/login`; passes everything else | | Add |
| WB-LOG-007 | `AuthorityRefreshFilter`: anonymous → skip; deleted user → logout; inactive → logout; "last active" throttle updates at most once per interval | | Partly in `StaffManagementIT`; Add throttle |

## 3.10 Other services (add as unit tests with Mockito)

| ID | Class | Paths to cover |
|---|---|---|
| WB-PAY-001 | `PaymentService.markPaid` | Claim not Approved; no `PAYMENT_RECORD`; date in the future; UTR blank; amount = approved amount; one payment per claim (unique constraint); audit and notification called |
| WB-NTF-001 | `ClaimNotifier.onTransition` | Recipient sets per action; the actor excluded; COO fallback when nobody holds `CLAIM_VERIFY`; the team's members; no recipients → no email |
| WB-NTF-002 | `NotificationService` | Mark read only for the owner; `readAll`; unread count |
| WB-NTF-003 | `SmtpEmailSender` / `LoggingEmailSender` | Send failure is caught and logged |
| WB-CMT-001 | `CommentService.add` | Blank; length 4000 / 4001; access check; notification; audit with the length only (no text) |
| WB-PRI-001 | `ClaimPriorityService` | `setPriority` without permission; request on a terminal claim; accept; decline; duplicate request |
| WB-QSV-001 | `ClaimQueueService` | Filters; scope per role; priority ordering is applied |
| WB-BLK-001 | Bulk decision | Empty list; mixed results (`BulkResult`); one failure does not roll back the others; four-eyes per claim |
| WB-TEAM-001 | `TeamService` | Default budget by program; custom budget; one team per user; remove member deactivates the user; audit |
| WB-USR-001 | `UserAdminService` | Invite: duplicate email, role not staff; hand-over: target must be active staff; the last COO; deactivate/reactivate |
| WB-TOK-001 | `UserTokenService` | Token is random and stored hashed; single use; expiry boundary; resend invalidates the old token |
| WB-AUD-001 | `AuditService` | `record` overloads write all fields; detail map is serialised; system actor `null` |
| WB-CAT-001 | `CategoryService` | Create / update / deactivate; duplicate name; flags |
| WB-SET-001 | `SettingsService` | Missing key → default; invalid number → clear error |
| WB-STO-001 | `LocalDiskStorageService` | Key from a UUID; path traversal impossible; delete of a missing file is a no-op |
| WB-STO-002 | `S3StorageService` | Put/get/delete with MinIO Testcontainer or a mock client |
| WB-WEB-001 | `GlobalExceptionHandler` | Mapping: `NotFound` → 404, `AccessDenied` → 403, `IllegalTransition`/`BusinessRule` → 409/422 or flash, `ConcurrentUpdate` → 409, the HTMX variant returns a fragment |
| WB-WEB-002 | `ViewHelpers`, `GlobalModelAdvice` | Escaping; null-safe formatting |
| WB-SEED-001 | `DemoDataSeeder` | Runs through real services; idempotent when run twice; off when `DEMO_DATA=false` |

## 3.11 Database-level white-box (Flyway V1, V2)

| ID | Target | Test |
|---|---|---|
| WB-DB-001 | `audit_log` trigger | `UPDATE` and `DELETE` raise an error; `INSERT` OK (`auditLogIsAppendOnly`) |
| WB-DB-002 | `STAFF_MANAGE` `CHECK` on grants | Direct insert fails |
| WB-DB-003 | `team_members.user_id` unique | Second insert fails |
| WB-DB-004 | `NUMERIC(12,2)` | 12345678901.00 fails; 3 decimals are rounded or rejected as designed |
| WB-DB-005 | Claim code sequence | Concurrent inserts get unique numbers |
| WB-DB-006 | Foreign keys | Delete of a referenced team / category refused |
| WB-DB-007 | Migrations | Apply to an empty DB, and Hibernate `ddl-auto: validate` passes |
| WB-DB-008 | Permissions seed | All 15 enum values present (`PermissionCatalogCheck`) |

## 3.12 Mutation testing (recommended)

Run PIT on the highest-risk packages and aim for a mutation score ≥ 80%:

```bash
mvn org.pitest:pitest-maven:mutationCoverage -DtargetClasses=com.nirmaan.reimburse.claim.*,com.nirmaan.reimburse.user.PermissionService -DtargetTests=com.nirmaan.reimburse.*Test
```

Typical mutants to kill: removing the four-eyes check, changing `<` to `<=` in the budget check, dropping the reason requirement, changing the 30-character threshold, removing the `nosniff` header.
