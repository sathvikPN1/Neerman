# Reusable prompt templates

Templates for recurring tasks in this repository, built from what worked in the prompts in files 01–04.
Fill in the `<…>` parts. Every template starts with the same constraint block.

## Constraint block (paste first)

```text
Project: Nirmaan Reimbursements (Spring Boot 3.5, Thymeleaf + HTMX, PostgreSQL, Flyway).
- Use Java 25 only (the <java.version> in pom.xml). Do not change the Java version or the Spring Boot version.
- Do not add, remove or upgrade dependencies or Maven plugins unless I ask. If you think one is needed,
  stop and tell me its exact groupId:artifactId and why. Do not guess package names.
- Preserve existing architecture: services enforce data scoping and permissions; controllers stay thin;
  schema changes go in a new Flyway migration (never edit an applied one, never ddl-auto=update).
- Secrets only from environment variables. Never print or commit a secret.
- Another developer pushes to main. Fetch first; never force-push, reset or discard others' work.
```

## Bug fix

```text
<constraint block>
Bug: <what happens> when <steps>. Expected: <what should happen>.
1. Reproduce it first (a failing test or exact steps) and show me the evidence.
2. Find the root cause and explain it in two sentences before changing code.
3. Make the smallest fix. No refactoring of unrelated code.
4. Add or extend a test that fails before the fix and passes after.
5. Run the full test suite and report the counts.
```

## Refactor

```text
<constraint block>
Refactor <class/package> to <goal, e.g. remove duplication between X and Y>.
- Behaviour must not change: public methods, URLs, template model attributes and DB schema stay the same.
- List the tests that cover this code before you start. If coverage is thin, add characterisation tests first.
- One logical change per commit.
```

## New screen or template change

```text
<constraint block>
Change <template> to match <mockup/section>.
- Use fragments/shell.html and the classes in static/design/app.css; don't add a CSS framework.
- Keep every th:*, sec:authorize and hx-* attribute and every form field name.
- Keep the text the integration tests assert on (grep src/test for the page's URL).
- Check it at 1280 px and 390 px. No horizontal scrolling.
```

## Documentation

```text
<constraint block>
Write <doc> for <audience>. Use the code as the source of truth: cite the class or file for every claim.
If something is unknown or not implemented, say so; don't fill gaps with plausible text.
```
