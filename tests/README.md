# Test Documentation: Nirmaan Reimbursement Platform

This folder holds the test plan and test cases for the platform. The automated code stays in `src/test/java` (Maven convention). The documents here describe **what is tested, why, and how to test it by hand**.

| # | Document | Purpose |
|---|---|---|
| 1 | [01-test-strategy.md](01-test-strategy.md) | Scope, test levels, techniques, environments, entry/exit criteria, risks |
| 2 | [02-black-box-test-cases.md](02-black-box-test-cases.md) | Behaviour tests per feature (equivalence classes, boundary values, decision tables, state transitions) |
| 3 | [03-white-box-test-cases.md](03-white-box-test-cases.md) | Code-level tests per class (statement, branch, path, condition coverage) |
| 4 | [04-security-tests.md](04-security-tests.md) | Authentication, authorisation, data isolation, upload, CSRF, session and audit tests |
| 5 | [05-non-functional-tests.md](05-non-functional-tests.md) | Concurrency, performance, usability, accessibility, compatibility, recovery |
| 6 | [06-test-data.md](06-test-data.md) | Demo accounts, boundary data sets, sample files |
| 7 | [07-traceability-matrix.md](07-traceability-matrix.md) | Feature → test case → automated test |
| 8 | [08-execution-and-defect-templates.md](08-execution-and-defect-templates.md) | Execution report and defect templates |
| – | [Test-Cases.xlsx](Test-Cases.xlsx) | All cases in Excel with filters and a Pass/Fail column. Rebuild with `python tests/tools/build_xlsx.py` |
| – | [smoke/smoke.sh](smoke/smoke.sh) | Quick HTTP smoke check against a running instance |

## Test case ID scheme

`TC-<AREA>-<NNN>` for black-box and security, `WB-<AREA>-<NNN>` for white-box, `NF-<AREA>-<NNN>` for non-functional.

| Area | Feature |
|---|---|
| AUTH | Login, logout, rate limit, session |
| INV | Staff invitation and account activation |
| USR | Staff, permissions, grants, COO hand-over |
| TEAM | Cohorts, teams, members, budgets |
| CAT | Expense categories |
| CLM | Claim create / edit / delete, warnings |
| DOC | Document upload, view, delete |
| WF | Workflow (submit, verify, return, reject, approve, reopen) |
| PRI | Priority flags and requests |
| COO | COO approvals screen, bulk approval |
| PAY | Finance payments |
| CMT | Comments |
| NTF | Notifications and email |
| AUD | Audit log |
| SEC | Security |
| DASH | Team dashboard, budget meter |

## Running the automated tests

```bash
mvn test                      # unit + integration tests (Docker must be running: Testcontainers starts PostgreSQL 16)
mvn test -Dtest=ClaimStateMachineTest      # a single class
mvn verify                    # includes the JaCoCo report: target/site/jacoco/index.html
```

## Running the manual tests

1. `docker compose up --build` (demo data is seeded; accounts are in [06-test-data.md](06-test-data.md)).
2. Run the smoke script: `bash tests/smoke/smoke.sh http://localhost:8080`.
3. Walk through the cases in [02-black-box-test-cases.md](02-black-box-test-cases.md) and record results using the template in [08](08-execution-and-defect-templates.md).

## Status of the cases

Each case has a **Auto** column:

- **ClassName** is covered by an existing automated test. A trailing **~** means the mapping was inferred from test names and must be confirmed.
- **Manual** is to be run by hand (mostly UI, visual or email checks).
- **Gap** is a case where automation is recommended but does not exist yet. See the gap list in [07](07-traceability-matrix.md#automation-gaps).

The "Auto" column was filled from the test method names. Confirm every "~" entry before you rely on it. When you add or rename an automated test, update the traceability matrix.
