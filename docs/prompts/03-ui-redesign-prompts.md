# UI redesign prompts (screens 01–18)

Prompts given to Claude Code (Claude Opus) while building the "Industry" redesign, 28 Oct – 8 Nov 2026,
with what each led to. Prompts are quoted verbatim, typos included. Screens 01–04 were built by Sathvik in
his own sessions; their prompts are not in this record.

## 1. Sync before starting

> "pull the official neerman code from this github: https://github.com/sathvikPN1/Neerman"

**Outcome:** the assistant found the folder was already a clone of that repository, fetched, and reported
that local `main` already matched `origin/main`, so there was nothing to pull. It asked for another link if
newer work was expected, instead of guessing.

## 2. The redesign brief

> "clone with the latest ui and push it to his github https://claude.ai/artifact/…"

**Context given:** a link to the design handoff (a bundled HTML page with 18 screen mockups, a design-token
stylesheet, fonts and the Lucide icon font), plus `docs/ui/screen-inventory.md` in the repository.

**Chain the assistant followed:**
1. Unpack the bundle and read the design tokens, components and every screen (`01 Sign in` … `18 Notifications`).
2. Map each mockup screen onto the existing Thymeleaf template it replaces.
3. Read every template first and keep all server logic (`th:*`, `sec:authorize`, HTMX attributes, form field names).
4. Find the integration tests that assert on page text, and keep those strings visible.
5. Build, run the full test suite, crawl every page as each demo role, and screenshot at 1280 px and 390 px.

**Outcome:** a full redesign was built and tested (182 tests passing at the time), but it was **not pushed**:
see the next prompt.

## 3. Day 2: continue on the other contributor's base

> "its day2 lets post remaining things"

**What the assistant found first:** 11 new upstream commits, including Sathvik's own implementation of
screens 01–04 with a different structure (`fragments/shell.html` and `static/design/design-system.css`).
The previous day's work, popped from a stash on top of them, had left conflict markers in 8 files.

**Decision:** don't push the day-1 version over his work. Instead, rebuild screens 05–18 on his shell and
CSS conventions, in a separate git worktree so the conflicted folder stayed untouched. The assistant's first
attempt to clean the conflicted folder with `git reset --hard` was blocked by the permission system. It
switched to the non-destructive worktree approach instead (to be recorded in the AI review log, Module 4).

## 4. Commit history

> "push things small big everything to github as commit history"

**Outcome:** 17 commits on `main` (`a81ce15` … `fd4c866`, 8 Nov): one per screen, plus one CSS bug fix and
one CSS helper commit. Each commit is reviewable on its own, and the push was a plain fast-forward.

## Techniques visible in these prompts

- **Short prompts, rich context.** The prompts are one line; the context (a mockup link, the repository and
  its tests) carries the detail. The assistant had to read that context before acting.
- **Verify the premise.** "Pull" and "clone" were checked against the actual repository state before
  anything was changed.
- **Prompt chaining across days.** Day 1 produced a design system and a page inventory. Day 2 reused them,
  after re-checking what had changed upstream overnight.
