# Course-module prompts (9 Nov 2026 onwards)

Prompts used to plan and deliver the CS5013 course modules (prompt engineering, AI workflow, review log,
tests, MCP reference, disclosure, CI and metrics, spec, security). Quoted verbatim, typos included.

## 1. The module list

A table pasted from the course planning notes: module number, what to add, and where. It also had two
shared-file rules:

> "pom.xml … This is the one shared file. Make it a single small commit."
> "README.md: adding links to the new docs. Add a section at the end rather than editing the middle."

with the request:

> "commit each of them at 9pm every night till 18th and there is another user so we need to be sure of it
> and can u see other users' schedule for commits"

**Outcome:** the assistant explained that git only shows *past* commits, not anyone's plans. It summarised
the other contributor's commit pattern from the history and recommended asking him for his planned windows.

## 2. The master prompt

A structured prompt with ten sections: role and objective; a mandatory repository and collaboration audit
before any change; per-module requirements; strict change control for `pom.xml` and `README.md`; the nightly
schedule; multi-contributor coordination; technical constraints; final verification; required reporting;
and "protect the shared repository above all else".

Its key constraints, quoted:

> "Never overwrite another contributor's uncommitted changes. Never discard, reset, or force-push shared work."
> "A schedule is not permission to commit incomplete or unverified work."
> "Do not create empty commits to satisfy the schedule."
> "Never fabricate coverage or complexity numbers."
> "Use actual development examples. Never fabricate rejected fixes or claim that a review occurred when it did not."

**Outcome:** the audit ran first and was committed as `docs/audit/2026-11-09-baseline.md` (`8079c4b`). It
changed the plan in three places, because the repository disagreed with the prompt's assumptions:
- two of the "untested" classes already had unit tests;
- JaCoCo was already configured;
- the project targets Java 25, not the Java 21 in the prompt's example constraint.

## 3. Short operational prompts

> "push it" — push the audit now, though it was before the agreed 9 pm slot.
>
> "commit everyday 1 1 task at 11am and ensure there are no merge conflicts with other user whos also
> pushing so push the coode now and ensure codebase is fine and go" — move the schedule to 11 am, one
> module per day, and fetch and check the other contributor's changes before every push.

## Techniques visible in these prompts

- **A master prompt with negative rules.** Most of its value is in the "never" lines. They rule out the
  shortcuts an assistant would otherwise take under schedule pressure: empty commits, invented metrics,
  force-pushes.
- **Audit before acting.** A whole section only asks for facts (branches, recent commits, Java version,
  existing tests). This caught assumptions in the prompt itself that were out of date.
- **Required reporting format.** A fixed nine-point report after each session makes every session easy to
  review.
