# Prompts

The prompts used to build this project with an AI coding assistant (Claude Code), versioned with the code
for the CS5013 *Programming with AI* course.

| File | What it is |
|---|---|
| [01-phase1-master-prompt.md](01-phase1-master-prompt.md) | The full specification prompt for Phase 1 (foundation and MVP) |
| [02-follow-up-prompts.md](02-follow-up-prompts.md) | Key follow-up prompts and what they led to |

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

New prompts are added here as the project continues (Phase 2 onwards).
