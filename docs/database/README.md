# Database Guide

PostgreSQL 16, schema managed by Flyway (`src/main/resources/db/migration`).

| Migration | Purpose |
|---|---|
| `V1__init.sql` | Tables, constraints, indexes, audit trigger |
| `V2__reference_data.sql` | Seed: permissions, role defaults, categories, settings |
| `V3__mcp_oauth.sql` | AI connector (MCP): OAuth clients, grants, codes, tokens, upload links |
| `V4__duplicate_protection.sql` | Unique index so an invoice is reimbursed only once |
| `V5__schema_comments.sql` | `COMMENT ON` for every table and column (docs only) |

> Never edit an applied migration (Flyway checksums). Add a new `V<n>__*.sql` instead.

Read the comments in psql with `\d+ claims`, or in pgAdmin / DBeaver.

## Conventions

- **Money**: `NUMERIC(12,2)`, INR.
- **Timestamps**: `TIMESTAMPTZ`, stored in UTC.
- **Keys**: `BIGSERIAL` surrogate `id`; natural keys only on `permissions.code` and `settings.key`.
- **Enums** are `VARCHAR` + `CHECK` (not PG enums), so adding a value is a simple migration.
- **Optimistic locking**: `version` column on `teams` and `claims`.
- **Soft delete**: `active` flag on `users`, `teams`, `categories`. Rows are not deleted.

## Tables by area

### Users & permissions
| Table | Description |
|---|---|
| `users` | Everyone who logs in. Email unique case-insensitively. |
| `user_roles` | User → role (COO, NIRMAAN_STAFF, FINANCE, TEAM_MEMBER). |
| `permissions` | Catalogue of permission codes (must match the Java `Permission` enum). |
| `role_default_permissions` | Permissions each role gets automatically. |
| `user_permissions` | Extra per-user grants, optionally expiring. `STAFF_MANAGE` can never be granted. |
| `user_tokens` | Single-use invite / password tokens (SHA-256 hash only). |

### Cohorts & teams
| Table | Description |
|---|---|
| `cohorts` | Programme intakes with start/end dates. |
| `teams` | Startup teams: program (PRATHAM / AKSHAR), budget, priority flag. |
| `team_members` | Student ↔ team. A student is in exactly one team. |

### Claims
| Table | Description |
|---|---|
| `categories` | Expense categories and their rules (allowed, pre-approval, justification). |
| `claims` | The reimbursement request; has a status workflow. |
| `claim_documents` | Attached files' metadata (invoice, proof). Max 10 MiB each. |
| `comments` | Discussion thread on a claim. |

### Payments
| Table | Description |
|---|---|
| `payout_batches` | A group of payments released together. |
| `payments` | One payment per claim (UNIQUE `claim_id`): amount, mode, UTR. |

### AI connector (MCP)
| Table | Description |
|---|---|
| `mcp_clients` | AI apps (ChatGPT, Claude) registered via OAuth dynamic client registration. |
| `mcp_grants` | A user's consent for one client; revocable. |
| `mcp_auth_codes` | Single-use OAuth codes (hash only, PKCE-bound). |
| `mcp_tokens` | Access (1 h) and refresh (30 d) token hashes. |
| `upload_links` | Time-limited links to attach a real file to an AI-drafted claim. |

### Cross-cutting
| Table | Description |
|---|---|
| `audit_log` | Append-only history; UPDATE/DELETE blocked by trigger. |
| `notifications` | In-app notifications per user. |
| `settings` | Runtime key/value config (budgets, SLAs). |

## Relationships (summary)

```
cohorts 1─* teams 1─* team_members *─1 users
teams   1─* claims *─1 categories
users   1─* claims (submitted_by / verified_by / approved_by)
claims  1─* claim_documents
claims  1─* comments
claims  1─0..1 payments *─0..1 payout_batches
users   1─* user_roles, user_permissions, user_tokens, notifications
mcp_clients 1─* mcp_grants *─1 users; mcp_grants 1─* mcp_auth_codes, mcp_tokens
claims  1─* upload_links *─1 users
permissions 1─* user_permissions, role_default_permissions
users   1─* audit_log (actor_id, on_behalf_of_id)
```

A proper ER diagram will follow in `docs/database/erd.md`.

## Claim status workflow

| Action | From | To |
|---|---|---|
| SUBMIT | DRAFT, RETURNED | SUBMITTED |
| VERIFY | SUBMITTED | VERIFIED |
| RETURN (reason required) | SUBMITTED, VERIFIED | RETURNED |
| REJECT (reason required) | SUBMITTED, VERIFIED | REJECTED |
| APPROVE | VERIFIED, SUBMITTED (COO only) | APPROVED |
| MARK_PAID | APPROVED | PAID |
| REOPEN (COO, reason required) | REJECTED, APPROVED | SUBMITTED |

Source: `ClaimAction.java`. The database only checks that `claims.status` is a valid value; transitions are enforced in `ClaimStateMachine` and recorded in `audit_log`.

## Notable constraints

| Rule | Where |
|---|---|
| `approved_amount <= claimed_amount` | CHECK on `claims` |
| One payment per claim | UNIQUE `payments.claim_id` |
| One team per student | UNIQUE index `ux_team_members_user` |
| `STAFF_MANAGE` not grantable | CHECK on `user_permissions`, `role_default_permissions` |
| Audit log immutable | Trigger `trg_audit_log_immutable` |
| Cohort `end_date >= start_date` | CHECK on `cohorts` |
| Upload size ≤ 10 MiB | CHECK on `claim_documents.size` |
| Same invoice reimbursed once | Unique index `ux_claims_vendor_invoice` (normalised vendor + invoice no.) |

## Known gaps

- `claims.pre_approval_id` has no FK: the pre-approval table arrives in Phase 2.
- `payout_batches.status` and `user_tokens.purpose` have no CHECK constraint; values are validated in Java.
- `role_default_permissions.role` has no CHECK matching `user_roles.role`.
