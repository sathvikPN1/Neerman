# Version control with an AI assistant

How the two contributors (Sathvik Narayan and Aishworya Shrivastav) use git when an AI coding assistant
(Claude Code) writes most of the code. These are the conventions the history already follows. Where it
doesn't follow them, this file says so.

## 1. Branching

- **Trunk-based on `main`.** Both contributors push small, finished commits straight to `main`. The history
  has no merge commits; every change is rebased onto the latest `main` before it is pushed.
- **Isolated work goes in a git worktree,** not in the shared working copy. Example: on 7 Nov the redesign
  of screens 05–18 was built in `../Neerman-day2` on a `ui-remaining-screens` branch, created from
  `origin/main`, while the main folder still had unresolved conflicts. Nothing in that folder had to be
  discarded to start.
- **Use a branch and a pull request** when a change touches the other person's recent work in a big way,
  or when it can't be split into small commits that are safe on their own.

## 2. Pull before work, check before push

At the start of every session, and again just before pushing:

```bash
git fetch origin
git log --format='%h %an %ad %s' --date=format:'%m-%d %H:%M' HEAD..origin/main   # what landed since
git log --name-only --format= HEAD..origin/main | sort -u                        # which files it touched
git pull --rebase origin main      # only with a clean working tree
```

- If the other contributor touched a file you are about to change, read their change first.
- **Never** force-push, `git reset --hard` a shared branch, or discard someone else's uncommitted work.
- The assistant follows the same rules. On 7 Nov its attempt to clean the conflicted folder with
  `git reset --hard` was blocked by Claude Code's permission check, and it switched to a worktree instead.
- **Shared files** get extra care. Changes to `pom.xml` go in one small commit with nothing else in it.
  New `README.md` content is added as a new section at the end, not edited into the middle.

## 3. Commit messages

```text
<Imperative summary, ≤ 72 characters>

<Why the change was needed and what it does, wrapped at ~72 characters.
How it was verified, if that isn't obvious.>

Co-Authored-By: <exact AI model> <noreply@anthropic.com>
```

- **One logical change per commit.** The redesign went in as one commit per screen (`be8becd` … `cbd428e`),
  and a bug fix found along the way (`a81ce15`, nested disclosure chevrons) went in its own commit before
  the feature commits that needed it.
- **Say what was verified** when it isn't obvious. For example, `dada573` says that
  `docker compose up --build` was run and that an uploaded PDF downloaded back byte-identical.
- **Check the subject before pushing.** `e8eab8a` was pushed with the editor's placeholder subject,
  "Your commit message". It is the only commit in the history without a real summary, and also the only
  one without an AI trailer.

## 4. Attributing AI contributions

The **git author is always the human** who reviewed and pushed the change. The assistant is credited with a
trailer:

```text
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

- **Name the exact model,** not just "AI". The history contains both `Claude Opus 5.5` (38 commits) and
  `Claude Sonnet 5.5` (6 commits).
- **Use the trailer only when the assistant actually wrote part of the change.** A purely human commit has
  no trailer. Never credit a real person who didn't work on the commit, and never claim an AI wrote
  something it didn't.
- **`Generated-by:`** (e.g. `Generated-by: ChatGPT`) is reserved for content produced by a tool other than
  Claude Code, such as a mock-up or a chat-written text pasted in. It is not in use yet: so far every
  change was made through Claude Code.
- The prompts behind the changes are versioned in [`docs/prompts/`](prompts/README.md). That way a reviewer
  can see what the assistant was asked, not just what it produced.

Snapshot on 10 Nov 2026: 44 of 45 commits on `main` carry a `Co-Authored-By: Claude …` trailer.

## 5. Human review before every push

The assistant may write the code, but a human is accountable for it, and the course viva expects each of us
to explain any line. Before pushing:

1. **Read the full diff** (`git diff --cached`), not just the assistant's summary of it.
2. **Run the tests** for anything that isn't documentation: `mvn verify`, all tests green. Integration tests
   need Docker for Testcontainers.
3. **For UI changes, look at the screens** at laptop and phone width. Tests check text, not layout.
4. **Check that nothing secret or unintended is staged**: no `.env` files, keys, build output or
   screenshots of real data.
5. **Check every claim in the commit message.** If it says "tested", it was tested.

The assistant reports what it ran and the results, including failures. A schedule is not a reason to push
unverified work: if a planned task isn't ready, report the blocker rather than making an empty or
misleading commit.

## 6. Resolving conflicts

1. **Stop and look** (`git status`, `git log origin/main`) before resolving anything. Find out whose work
   is on the other side.
2. **Prefer the other contributor's structure** when both of you built the same thing. Example: on 7 Nov,
   upstream already had Sathvik's version of screens 01–04, built on a new shared shell. Re-applying the
   local day-1 redesign on top left conflict markers in 8 files. Instead of resolving those in favour of
   the local version, screens 05–18 were rebuilt on his shell and pushed as a fast-forward (`fd4c866`).
3. **Keep the losing version recoverable:** stash it or keep it on a branch, and don't delete it.
4. **After resolving, re-run the full test suite** before pushing, even if the conflict looked trivial.
5. **When in doubt, ask the other contributor** rather than letting the assistant guess.
