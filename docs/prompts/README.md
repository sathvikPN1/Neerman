# Prompts

The prompts used to build this project with an AI coding assistant (Claude Code), versioned with the code
for the CS5013 *Programming with AI* course.

| File | What it is |
|---|---|
| [01-phase1-master-prompt.md](01-phase1-master-prompt.md) | The full specification prompt for Phase 1 (foundation and MVP) |
| [02-follow-up-prompts.md](02-follow-up-prompts.md) | Key follow-up prompts and what they led to |
| [03-ui-redesign-prompts.md](03-ui-redesign-prompts.md) | The UI redesign (screens 05–18): brief, day-2 rework onto the shared shell, commit history |
| [04-course-module-prompts.md](04-course-module-prompts.md) | The course-module master prompt, the audit it triggered, and scheduling prompts |
| [05-reusable-templates.md](05-reusable-templates.md) | Reusable templates for bug fixes, refactors, screens and docs, with a shared constraint block |
| *MCP connector prompt* | **To be added by Sathvik.** The prompts for the AI connector (`mcp/`, commit `a3f2cec`) were written in his sessions and aren't in this record. |

## Prompt-engineering techniques used

- **Instruction first:** the task is stated in the first line and repeated at the end.
- **Context:** the stakeholder (Nirmaan, IIT Madras), the problem, the user roles and the business rules from
  the Nirmaan Student Guide.
- **Mandatory stack and constraints:** exact technologies, plus explicit must/must-not rules (no
  `ddl-auto=update`, secrets only from environment variables, data scoping enforced in services).
- **Structure:** numbered sections separating background, rules, data model, phases and working method.
- **Phases with checkpoints:** the assistant stops and summarises after each phase for human review.
- **Acceptance criteria:** named tests that must pass (every state transition, the four-eyes rule, grant
  expiry, ≥ 80% service coverage).
- **Ambiguity handling:** decisions are recorded in [`ASSUMPTIONS.md`](../../ASSUMPTIONS.md), not guessed silently.
- **Iterative refinement:** vague follow-ups are turned into concrete, checkable rules.
- **Rich context:** course web pages, screenshots and full terminal output are given to the assistant as
  source material.

## Prompt chaining, negative prompting and constraints

**Prompt chaining.** Large tasks are split into prompts whose output feeds the next. Examples: the Phase 1
master prompt, then follow-ups that refine it; the redesign brief, then a page inventory, then one prompt per
screen; and for the course modules, an audit prompt whose findings changed the plan for every later module.
Each link in the chain is checked (tests, screenshots, a diff) before the next one starts, so an error
doesn't carry forward.

**Negative prompting.** Saying what *not* to do closes the shortcuts an assistant takes under pressure. The
most useful negative rules in this project:
- "Never force-push, reset or discard another contributor's work."
- "Do not create empty commits to satisfy the schedule."
- "Never fabricate coverage numbers, test results or review-log entries."
- "No `ddl-auto=update`."

**Strict technical constraints.** Name the exact version and say what is off-limits, e.g.:

> "Use Java 25 only (the version in `pom.xml`). Do not change the Java or Spring Boot version. Preserve
> existing architectural decisions."

The course example says Java 21; this project targets Java 25, so the constraint follows `pom.xml`. A
constraint copied without checking the repository would itself cause drift.

**Preventing unintended technology or dependency changes.** Every template in
[05-reusable-templates.md](05-reusable-templates.md) begins with the same constraint block:
- No new, removed or upgraded dependencies or plugins unless asked.
- If one seems needed, the assistant must stop and name the exact `groupId:artifactId`. This guards against
  hallucinated package names (slopsquatting).
- Changes to `pom.xml`, the one shared build file, go in a single isolated commit.

New prompts are added here as the project continues.
