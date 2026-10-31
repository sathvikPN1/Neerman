# 8. Execution Report and Defect Templates

## 8.1 Test execution report

Copy this section into `tests/reports/<yyyy-mm-dd>-<build>.md` for each cycle.

```markdown
# Test execution report

- Build / commit:
- Environment: (local compose / staging)  Browser(s):
- Tester:               Date:
- Automated: `mvn test` → tests run: __  failures: __  errors: __  skipped: __
- Coverage (JaCoCo): service line __%  branch __%

## Summary
| Area | Planned | Passed | Failed | Blocked | Not run |
|---|---|---|---|---|---|
| AUTH | | | | | |
| INV / USR | | | | | |
| TEAM / CAT | | | | | |
| CLM / DOC | | | | | |
| WF / PRI / COO / PAY | | | | | |
| CMT / NTF / AUD / DASH | | | | | |
| E2E | | | | | |
| SEC | | | | | |
| NF | | | | | |

## Detail
| ID | Result (Pass/Fail/Blocked/N/A) | Actual result / evidence | Defect # |
|---|---|---|---|

## Exit criteria
- [ ] All P1 cases pass
- [ ] ≥ 95% of P2 pass
- [ ] No open Critical/High defects
- [ ] Coverage at or above the baseline
- [ ] Smoke script passes on the release build

## Sign-off
Tester:            Product owner:            Date:
```

## 8.2 Defect report

```markdown
# DEF-<number>: <short title>

- Severity: Critical / High / Medium / Low
- Priority: P1 / P2 / P3
- Found in build:             Environment:
- Test case: TC-...
- Reporter:                   Date:

## Steps to reproduce
1.
2.
3.

## Expected result

## Actual result

## Evidence
Screenshot / log lines / request and response / SQL.

## Impact
Who is affected and what the risk is (money, data exposure, blocked flow).

## Suspected cause (optional)
File and line.

## Fix verification
- [ ] Regression test added (id: ...)
- [ ] Re-tested in build:
```

### Severity guide

| Severity | Examples in this system |
|---|---|
| Critical | Another team's data visible; an approval above budget; a claim paid twice; audit log editable; privilege escalation |
| High | The workflow is blocked; the wrong amount paid or displayed; a notification to the wrong user; a permission change not applied |
| Medium | Validation message wrong; a filter wrong; a layout broken on mobile; a workaround exists |
| Low | Typo, spacing, colour |

## 8.3 Exploratory testing charter

```markdown
Charter: Explore <area> with <resource/role> to discover <risk>.
Time box: 60 minutes.   Tester:   Date:
Notes (what was tried):
Bugs found:
Questions / ideas for new cases:
```

Suggested charters:

1. Explore the claim form with unusual amounts, dates and Unicode, to discover validation and display bugs.
2. Explore the COO approvals screen on a phone while on a slow network, to discover HTMX and double-submit problems.
3. Explore permission changes while the affected user is mid-task, to discover stale-access bugs.
4. Explore uploads with unusual files (photos from different phones, scanned PDFs, large files), to discover rejection and preview problems.
5. Explore two browser tabs open on one claim, to discover stale-version handling.
