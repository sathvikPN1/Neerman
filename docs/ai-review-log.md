# AI review log: rejected and reworked suggestions

Changes proposed or first written by the AI assistant (Claude Code, Claude Opus 5.5) that were **rejected**
or **reworked** before they reached `main`, with the reason and how the final version was checked.
Only real events from the sessions of 7–9 November 2026 are listed. Each one can be traced to a commit, to
the saved day-1 work (`stash@{0}`, "ui-redesign-wip"), or to a check that was actually run.

Sathvik's earlier sessions (Phase 1, screens 01–04, the MCP connector) are not covered here; their review
notes would come from his own sessions.

| # | Date | Area | Decision |
|---|---|---|---|
| 1 | 7 Nov | Whole day-1 redesign | Rejected, rebuilt on the other contributor's base |
| 2 | 7 Nov | `git reset --hard` on the conflicted folder | Rejected (blocked), replaced by a worktree |
| 3 | 28 Oct | `initials()` regex in `ViewHelpers` | Reworked: escaping bug |
| 4 | 28 Oct | Button and link colours vs Tailwind preflight | Reworked: CSS specificity |
| 5 | 7 Nov | Nested disclosure chevrons (existing CSS) | Reworked: selector too broad |
| 6 | 7 Nov | Conditional status tag in the queue | Reworked: Thymeleaf attribute order |
| 7 | 7 Nov | Inactive-row styling in categories and staff | Reworked: `th:style` dropped the whole attribute |
| 8 | 9 Nov | Replacement image for MinIO in Docker Compose | Two images rejected, design changed |
| 9 | 9 Nov | Script that created the MCP test account | Reworked: lost the generated password |
| 10 | 9 Nov | Course master prompt's own assumptions | Corrected from the audit, not followed blindly |
| 11 | 9 Nov | MCP connector prompt for `docs/prompts/` | Not written: left for its author |

---

### 1. Day-1 redesign pushed over upstream: rejected

- **Suggestion:** the assistant had built a full redesign of all 18 screens on Tailwind plus its own
  `app.css` (182 tests passing). The plan was to push it the next day.
- **Why it was wrong:** overnight, Sathvik pushed his own implementation of screens 01–04 with a different
  structure (`fragments/shell.html`, `static/design/design-system.css`). Popping the day-1 stash on top of
  that left conflict markers in 8 files. Resolving them in favour of the day-1 version would have replaced
  his work.
- **What was done instead:** screens 05–18 were rebuilt on his shell and CSS conventions, one commit per
  screen (`be8becd` … `fd4c866`). The day-1 work is kept in `stash@{0}`, not deleted.
- **Verified by:** `mvn verify` (209 tests, 0 failures); a crawl of every page as all six demo roles
  (all HTTP 200, none still on the old layout); screenshots at 1280 px and 390 px.

### 2. `git reset --hard` to clean the conflicted folder: rejected

- **Suggestion:** reset the shared working folder to `HEAD` to remove the conflict markers.
- **Why it was wrong:** it is destructive and irreversible for uncommitted work. Claude Code's permission
  check blocked it ("Irreversible Local Destruction").
- **What was done instead:** a separate worktree from `origin/main` (`git worktree add ../Neerman-day2 …`).
  The conflicted folder was left exactly as it was, for the owner to clean up later.
- **Verified by:** `git stash list` still shows `stash@{0}`, and the owner later cleaned the folder
  themselves.

### 3. Regex in `ViewHelpers.initials()`: reworked

- **AI output:** a generated edit script wrote `name.trim().split("\s+")`. The double backslash was lost on
  the way through the shell.
- **Why it was wrong:** in Java 15+ `"\s"` is a valid escape for a single **space**. So it compiles, but it
  splits on exactly one space instead of any whitespace. A name with a double space or a tab would get wrong
  initials, and no compiler error would show it.
- **Fix:** `split("\\s+")`, caught by reading the diff right after the edit.
- **Verified by:** the diff (now in `stash@{0}`) and a successful build. *Gap:* no unit test was written for
  it, because this code was not pushed.

### 4. Button and link colours lost to Tailwind's reset: reworked

- **AI output:** components styled with single classes (`.btn-primary`, plain `a { color }`).
- **Why it was wrong:** the Tailwind CDN injects its preflight styles *after* `app.css`.
  `button[type=submit]` (specificity 0,1,1) beat `.btn-primary` (0,1,0), and preflight's `a { color: inherit }`
  overrode the link colour. Screenshots showed links in body colour.
- **Fix:** variant selectors with two classes (`.btn.btn-primary`), and a comment in the CSS explaining why.
- **Verified by:** re-taken screenshots. This code is in `stash@{0}` only: the final redesign uses
  Sathvik's design-system CSS, which has no Tailwind.

### 5. Chevrons inside nested `<details>`: reworked (existing CSS)

- **Found by:** a 390 px screenshot of the COO approval card. "Approve part of it" had no chevron.
- **Cause:** `details.expand[open] .chev-down` matched *every* descendant, so a closed `<details>` inside an
  open card lost both chevrons.
- **Fix:** scope it to each element's own summary: `details.expand[open] > summary .chev-down`. Committed on
  its own before the screens that needed it (`a81ce15`).
- **Verified by:** re-rendered screenshots; full test suite green.

### 6. `th:if` together with `th:replace`: reworked

- **AI output:** `<span th:if="${r.status().name() != 'SUBMITTED'}" th:replace="~{fragments/shell :: status(…)}">`.
- **Why it was wrong:** in Thymeleaf, fragment inclusion (`th:replace`) runs **before** `th:if`, so the
  condition would not reliably stop the tag from rendering.
- **Fix:** wrap it in `<th:block th:if="…">`, with the replace inside (in `484e965`). The same pattern was
  used everywhere else in the redesign.
- **Verified by:** the page crawl, and reading the rendered verification-queue HTML.

### 7. `th:style` on inactive rows: reworked

- **AI output:** `style="border-bottom:…" th:style="${!c.active()} ? 'border-bottom:…;opacity:.6'"`.
- **Why it was wrong:** for active rows the expression is `null`, and Thymeleaf then **removes the whole
  `style` attribute**. All row dividers disappeared from the categories list. A screenshot showed it; the
  tests did not.
- **Fix:** `th:styleappend="${!c.active()} ? 'opacity:.6'"`, which keeps the static style (in `078861c` and
  `aa6268c`).
- **Verified by:** the rendered categories page has a divider on all 11 rows.

### 8. Replacement for the MinIO image: two rejected, design changed

- **Problem:** `docker compose up` failed: `minio/minio` (Docker Hub and Quay) can no longer be pulled.
- **Tried and rejected:**
  - `cgr.dev/chainguard/minio`: it runs as a non-root user and failed with "file access denied" on `/data`.
    It also has no `mc` or `curl`, so the compose health check could never pass.
  - `bitnamilegacy/minio`: it pulls, but it is a frozen, unmaintained image. Not suitable as the default.
- **What was done instead:** the app already supports local-disk storage, and `STORAGE_TYPE` defaults to
  `local`. Compose now uses that on an `uploads` volume. MinIO moved behind an opt-in `s3` profile with a
  configurable image (`dada573`).
- **Verified by:** `docker compose up --build` started all services; a PDF uploaded as a team member
  downloaded back byte-identical.

### 9. MCP test-account script lost the password: reworked

- **AI output:** a script that created a team-member account through the real invite flow, generated a
  password, ran the OAuth flow and printed the password **at the end**.
- **Why it was wrong:** printing `₹` crashed Python on the Windows console *before* the password line was
  reached. The account was active, but nobody knew its password.
- **Fix:** the script now writes the credentials to a file the moment the password is set, and output comes
  last. To recover, the one account's `password_hash` was cleared in the **local dev database only**, and
  the normal resend-invite flow was used again.
- **Product gap found:** there is **no password reset for an activated account**. `resendInvite` refuses
  once a password is set, yet the login page's "Forgot password?" tells users to ask staff to resend their
  invite. Not fixed yet; to go into the spec (Module 8) as a known mismatch.
- **Verified by:** signing in with the new password, and a fresh OAuth run that listed all 10 MCP tools.

### 10. The course master prompt's assumptions: corrected

- **Prompt said:** prioritise unit tests for `FileTypeDetector`, `DuplicateClaimGuard` and
  `LoginAttemptService`; add JaCoCo; use "Java 21 only" as the example constraint.
- **The audit found** (`docs/audit/2026-11-09-baseline.md`):
  - `FileTypeDetectorTest` and `LoginAttemptServiceTest` already existed;
  - JaCoCo was already configured;
  - the project targets **Java 25**.
- **What was done instead:** the plan targets the real gaps (`DuplicateClaimGuard` unit tests and the
  `McpTools` branches), the `pom.xml` change only adds static analysis, and the documented constraint says
  Java 25.

### 11. MCP connector prompt: not written

- **Request:** add "the MCP integration prompt" to `docs/prompts/`.
- **Why it wasn't done:** that prompt was written in Sathvik's sessions and is not in this record. Writing a
  plausible-looking one would have been fabrication.
- **What was done instead:** a clearly marked slot in `docs/prompts/README.md` for him to fill in.
