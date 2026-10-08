# Assumptions

Where the brief was ambiguous, we chose the simplest option that fits the business rules. Each one is listed here so it can be revisited.

## Platform

1. **Java version.** The brief asks for Java 21. During Phase 1, `pom.xml` was changed outside this work to `<java.version>25</java.version>`. The `Dockerfile` follows the pom through `ARG JAVA_VERSION` (default 25). To go back to 21, change both.
2. **Single application instance.** The login rate limiter and the "last active" throttle keep their state in memory. Running several instances needs a shared store (e.g. Redis).
3. **Email is best effort.** Emails are sent after the database transaction commits. A failed send is logged and not retried; the in-app notification is the source of truth. For the same reason, the mail health indicator is disabled, so an SMTP outage does not mark the app unhealthy.

## Users, roles and permissions

4. **One team per student.** This is enforced by a unique index on `team_members.user_id`.
5. **Default bundles are copied at invite time.** A new staff or finance user receives their role's default permissions as explicit grants. Editing a role's defaults later affects only users invited afterwards.
6. **Exactly one COO.** The COO cannot be invited; the role only moves through hand-over. On hand-over the outgoing COO becomes Nirmaan staff with the current default staff bundle. The new COO's explicit grants are cleared, because the COO implicitly holds every permission.
7. **Team members never get back-office permissions.** The permission editor refuses them.
8. **Grant expiry.** The date picked in the UI is the last day, inclusive. The grant ends at midnight IST at the end of that day. An expired grant stops working immediately, because access checks ignore it. A cleanup job then deletes it every minute and audit-logs `PERMISSION_EXPIRED` with the system as actor.
9. **Immediate effect.** The signed-in principal is rebuilt from the database on every request. Permission changes, expiries, hand-over and deactivation therefore apply on the user's next click. Deactivated users are signed out.
10. **Login rate limit.** After 5 failed attempts for an email, that email is locked for 15 minutes. Both numbers are configurable.

## Claim workflow

11. **Only the team submits.** Submitting is the team's own declaration, so not even the COO submits on a team's behalf. The COO can do everything else.
12. **Partial approval is part of APPROVE.** There is one APPROVE action. An approved amount below the claimed amount is a partial approval and needs a reason. It is audited as `PARTIALLY_APPROVED`.
13. **The COO can verify and approve in one step,** directly from SUBMITTED. This is audited as `VERIFIED_AND_APPROVED`, with the COO recorded as verifier. Nobody else can skip verification.
14. **Return or reject from VERIFIED** is allowed for holders of `CLAIM_APPROVE` (the approver's decision) or `CLAIM_RETURN_REJECT`.
15. **Approvals cannot overspend.** An approval above the team's remaining budget is refused with the exact remaining amount, so the approver can partially approve up to it. The team row is locked (`SELECT … FOR UPDATE`) during approval, so two concurrent approvals cannot overspend together.
16. **COO override (REOPEN).** REJECTED or APPROVED (unpaid) claims can be reopened to SUBMITTED, with a reason. Verification and approval are cleared. PAID claims cannot be reopened because the money has already moved.
17. **State is checked before permissions.** An action that is illegal in the current state returns a clear message rather than a 403.
18. **Claim codes.** The format is `NRM-<year>-<5-digit number>`. The year is the creation year in IST. The number comes from one global sequence and does not reset each year.
19. **Draft fields.** Category, amount, expense date, vendor and justification are required even for a draft. GSTIN, invoice number and invoice date are optional in Phase 1. The GSTIN format is validated when given; Phase 2 adds the invoice checklist.
20. **"Other" category.** It needs a justification of at least 30 characters.
21. **Pre-approval categories (Travel)** cannot be submitted until pre-approvals ship in Phase 2. The rule is enforced now.
22. **Deleting drafts.** Only drafts that were never submitted can be deleted. A returned claim is edited and resubmitted instead.
23. **Removing a team member** deactivates their account. Their claims stay with the team.

## Notifications, queues, documents

24. **Users are never notified of their own actions.**
25. **Who hears about submissions.** Staff holding `CLAIM_VERIFY` are notified, not including the COO unless nobody else can verify. Holders of `CLAIM_APPROVE`, including the COO, hear about verified claims. Phase 3's daily digest will reduce the COO's email.
26. **Queues are sorted in memory**: effective priority first (claim or team), then oldest in the current stage. Volumes are tens of claims, not thousands.
27. **Documents.** Storage keys are random UUIDs. A file is removed from storage after its document row is deleted, or when the upload's transaction rolls back. HEIC files are accepted but browsers cannot preview them, so they are offered as downloads.

## Deferred to later phases

28. **Pratham → Akshar.** In Phase 1, staff can change a team's program and budget from the team edit page (audit-logged). The dedicated promotion flow with budget history comes in Phase 3.
29. **SLA targets** are stored in `settings` (2 working days for priority, 5 for normal). The reminders that use them come in Phase 3.
30. **Bank details and batch payouts** come in Phase 2. The finance queue currently shows claims and approved amounts without bank details.
