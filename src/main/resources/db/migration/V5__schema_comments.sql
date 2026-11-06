-- Documentation-only migration: attaches COMMENT ON descriptions to every table and
-- column created in V1. No structural or data change. Visible in psql (\d+ table),
-- pgAdmin, DBeaver and any schema-doc generator. See docs/database/README.md.

-- ================================================================ users & permissions
COMMENT ON TABLE  users IS 'Every person who can log in: students (TEAM_MEMBER), Nirmaan staff, Finance and the COO.';
COMMENT ON COLUMN users.id             IS 'Surrogate primary key.';
COMMENT ON COLUMN users.name           IS 'Display name.';
COMMENT ON COLUMN users.email          IS 'Login identifier. Unique case-insensitively (index ux_users_email on lower(email)).';
COMMENT ON COLUMN users.password_hash  IS 'BCrypt hash. NULL until the user accepts their invite and sets a password.';
COMMENT ON COLUMN users.active         IS 'FALSE = deactivated; the user can no longer log in. Rows are never deleted.';
COMMENT ON COLUMN users.created_at     IS 'When the account was created (UTC).';
COMMENT ON COLUMN users.last_active_at IS 'Last authenticated activity (UTC). NULL if the user never logged in.';

COMMENT ON TABLE  user_roles IS 'Role(s) held by a user. Junction table: a user may hold several roles.';
COMMENT ON COLUMN user_roles.user_id IS 'FK to users.id. Deleting the user removes their roles.';
COMMENT ON COLUMN user_roles.role    IS 'One of COO, NIRMAAN_STAFF, FINANCE, TEAM_MEMBER (enforced by CHECK).';

COMMENT ON TABLE  permissions IS 'Catalogue of fine-grained permission codes. Seeded in V2; must match the Permission enum in Java (checked at start-up).';
COMMENT ON COLUMN permissions.code        IS 'Natural primary key, e.g. CLAIM_VERIFY.';
COMMENT ON COLUMN permissions.description IS 'Human-readable explanation shown in the admin UI.';

COMMENT ON TABLE  user_permissions IS 'Permissions granted to an individual user on top of their role defaults, optionally time-limited.';
COMMENT ON COLUMN user_permissions.id              IS 'Surrogate primary key.';
COMMENT ON COLUMN user_permissions.user_id         IS 'FK to users.id: who receives the permission.';
COMMENT ON COLUMN user_permissions.permission_code IS 'FK to permissions.code. STAFF_MANAGE can never be granted (ck_staff_manage_not_grantable).';
COMMENT ON COLUMN user_permissions.granted_by      IS 'FK to users.id: the COO/admin who granted it.';
COMMENT ON COLUMN user_permissions.granted_at      IS 'When the grant was made (UTC).';
COMMENT ON COLUMN user_permissions.expires_at      IS 'Grant lapses after this instant. NULL = never expires.';

COMMENT ON TABLE  role_default_permissions IS 'Permissions every holder of a role gets automatically. Seeded in V2.';
COMMENT ON COLUMN role_default_permissions.role            IS 'Role name (same vocabulary as user_roles.role).';
COMMENT ON COLUMN role_default_permissions.permission_code IS 'FK to permissions.code. STAFF_MANAGE is excluded by CHECK.';

COMMENT ON TABLE  user_tokens IS 'Single-use tokens for invites and password set-up. Only the SHA-256 hash of the token is stored.';
COMMENT ON COLUMN user_tokens.token_hash IS 'SHA-256 hex of the emailed token. Primary key.';
COMMENT ON COLUMN user_tokens.user_id    IS 'FK to users.id: the user the token was issued to.';
COMMENT ON COLUMN user_tokens.purpose    IS 'What the token allows, e.g. invite or password reset.';
COMMENT ON COLUMN user_tokens.expires_at IS 'Token is invalid after this instant.';
COMMENT ON COLUMN user_tokens.used_at    IS 'Set when the token is redeemed; NULL = still unused.';
COMMENT ON COLUMN user_tokens.created_at IS 'When the token was issued (UTC).';

-- ================================================================ cohorts & teams
COMMENT ON TABLE  cohorts IS 'A programme intake (batch) of teams, with a start and end date.';
COMMENT ON COLUMN cohorts.id         IS 'Surrogate primary key.';
COMMENT ON COLUMN cohorts.name       IS 'Unique cohort name.';
COMMENT ON COLUMN cohorts.start_date IS 'First day of the cohort.';
COMMENT ON COLUMN cohorts.end_date   IS 'Last day of the cohort. Must be >= start_date.';
COMMENT ON COLUMN cohorts.created_at IS 'Row creation time (UTC).';

COMMENT ON TABLE  teams IS 'A student startup team that owns a reimbursement budget and submits claims.';
COMMENT ON COLUMN teams.id              IS 'Surrogate primary key.';
COMMENT ON COLUMN teams.name            IS 'Unique team name.';
COMMENT ON COLUMN teams.cohort_id       IS 'FK to cohorts.id: the cohort the team belongs to.';
COMMENT ON COLUMN teams.program         IS 'PRATHAM (early stage) or AKSHAR (later stage); decides the default budget.';
COMMENT ON COLUMN teams.budget_amount   IS 'Total reimbursable budget in INR, NUMERIC(12,2), >= 0.';
COMMENT ON COLUMN teams.priority        IS 'TRUE = team''s claims are fast-tracked (shorter SLA).';
COMMENT ON COLUMN teams.priority_reason IS 'Why the team is flagged priority.';
COMMENT ON COLUMN teams.active          IS 'FALSE = team closed; no new claims.';
COMMENT ON COLUMN teams.created_at      IS 'Row creation time (UTC).';
COMMENT ON COLUMN teams.version         IS 'Optimistic-locking counter (JPA @Version).';

COMMENT ON TABLE  team_members IS 'Membership of students in teams. A student belongs to exactly one team (unique index ux_team_members_user).';
COMMENT ON COLUMN team_members.team_id IS 'FK to teams.id.';
COMMENT ON COLUMN team_members.user_id IS 'FK to users.id. Unique across the table.';

-- ================================================================ categories
COMMENT ON TABLE  categories IS 'Expense categories and the rules that govern them. Seeded in V2, editable with CATEGORY_MANAGE.';
COMMENT ON COLUMN categories.id                       IS 'Surrogate primary key.';
COMMENT ON COLUMN categories.name                     IS 'Unique category name.';
COMMENT ON COLUMN categories.allowed                  IS 'FALSE = not reimbursable under the Nirmaan guide; claims are blocked.';
COMMENT ON COLUMN categories.requires_pre_approval    IS 'TRUE = an approved pre-approval is mandatory before claiming (e.g. Travel).';
COMMENT ON COLUMN categories.pre_approval_recommended IS 'TRUE = UI nudges the team to seek pre-approval but does not block.';
COMMENT ON COLUMN categories.justification_required   IS 'TRUE = claims must carry a written justification (e.g. Other).';
COMMENT ON COLUMN categories.description              IS 'Rule text shown to students.';
COMMENT ON COLUMN categories.sort_order               IS 'Display order; lower first.';
COMMENT ON COLUMN categories.active                   IS 'FALSE = hidden from new claims.';

-- ================================================================ claims
COMMENT ON SEQUENCE claim_code_seq IS 'Counter used to generate claims.public_code.';

COMMENT ON TABLE  claims IS 'A reimbursement request raised by a team. Core entity; moves DRAFT > SUBMITTED > VERIFIED > APPROVED > PAID (or RETURNED / REJECTED).';
COMMENT ON COLUMN claims.id                      IS 'Surrogate primary key.';
COMMENT ON COLUMN claims.public_code             IS 'Human-friendly unique reference shown in the UI and emails.';
COMMENT ON COLUMN claims.team_id                 IS 'FK to teams.id: the team being reimbursed.';
COMMENT ON COLUMN claims.submitted_by            IS 'FK to users.id: the student who raised the claim.';
COMMENT ON COLUMN claims.category_id             IS 'FK to categories.id.';
COMMENT ON COLUMN claims.pre_approval_id         IS 'Reserved for Phase 2 pre-approvals. No FK yet because the target table does not exist.';
COMMENT ON COLUMN claims.claimed_amount          IS 'Amount requested, INR, > 0.';
COMMENT ON COLUMN claims.approved_amount         IS 'Amount approved, INR. NULL until decided; must be <= claimed_amount.';
COMMENT ON COLUMN claims.expense_date            IS 'Date the money was spent.';
COMMENT ON COLUMN claims.vendor_name             IS 'Seller / service provider on the invoice.';
COMMENT ON COLUMN claims.vendor_gstin            IS 'Vendor GST number (15 chars), if the invoice has one.';
COMMENT ON COLUMN claims.invoice_number          IS 'Invoice number printed on the bill.';
COMMENT ON COLUMN claims.invoice_date            IS 'Date printed on the invoice.';
COMMENT ON COLUMN claims.justification           IS 'Student''s explanation of how the expense benefits the product.';
COMMENT ON COLUMN claims.status                  IS 'Workflow state: DRAFT, SUBMITTED, VERIFIED, RETURNED, APPROVED, PAID, REJECTED.';
COMMENT ON COLUMN claims.priority                IS 'TRUE = fast-tracked (shorter SLA). Set by staff with CLAIM_FLAG_PRIORITY.';
COMMENT ON COLUMN claims.priority_reason         IS 'Why staff marked the claim priority.';
COMMENT ON COLUMN claims.priority_requested      IS 'TRUE = the student asked for priority handling.';
COMMENT ON COLUMN claims.priority_request_reason IS 'Student''s reason for requesting priority.';
COMMENT ON COLUMN claims.verified_by             IS 'FK to users.id: staff member who verified the claim.';
COMMENT ON COLUMN claims.approved_by             IS 'FK to users.id: COO (or delegate) who approved it.';
COMMENT ON COLUMN claims.decision_reason         IS 'Reason given when returning or rejecting.';
COMMENT ON COLUMN claims.created_at              IS 'Row creation time (UTC).';
COMMENT ON COLUMN claims.updated_at              IS 'Last modification time (UTC).';
COMMENT ON COLUMN claims.submitted_at            IS 'When the student submitted it. NULL while DRAFT.';
COMMENT ON COLUMN claims.stage_entered_at        IS 'When the claim entered its current status; drives SLA timers.';
COMMENT ON COLUMN claims.verified_at             IS 'Verification timestamp.';
COMMENT ON COLUMN claims.approved_at             IS 'Approval timestamp.';
COMMENT ON COLUMN claims.paid_at                 IS 'Payment timestamp.';
COMMENT ON COLUMN claims.version                 IS 'Optimistic-locking counter (JPA @Version).';

COMMENT ON TABLE  claim_documents IS 'Files (invoices, payment proofs) attached to a claim. File bytes live in storage; this table holds metadata.';
COMMENT ON COLUMN claim_documents.id                IS 'Surrogate primary key.';
COMMENT ON COLUMN claim_documents.claim_id          IS 'FK to claims.id. Documents are deleted with the claim.';
COMMENT ON COLUMN claim_documents.type              IS 'INVOICE, PAYMENT_PROOF or OTHER.';
COMMENT ON COLUMN claim_documents.storage_key       IS 'Unique path/key of the file in the storage backend.';
COMMENT ON COLUMN claim_documents.original_filename IS 'Filename as uploaded by the user.';
COMMENT ON COLUMN claim_documents.content_type      IS 'MIME type, e.g. application/pdf.';
COMMENT ON COLUMN claim_documents.size              IS 'File size in bytes; 1 to 10485760 (10 MiB).';
COMMENT ON COLUMN claim_documents.sha256            IS 'SHA-256 hex digest; used to detect duplicate uploads.';
COMMENT ON COLUMN claim_documents.uploaded_by       IS 'FK to users.id: uploader.';
COMMENT ON COLUMN claim_documents.uploaded_at       IS 'Upload time (UTC).';

COMMENT ON TABLE  comments IS 'Discussion thread on a claim between students and staff.';
COMMENT ON COLUMN comments.id         IS 'Surrogate primary key.';
COMMENT ON COLUMN comments.claim_id   IS 'FK to claims.id. Comments are deleted with the claim.';
COMMENT ON COLUMN comments.author_id  IS 'FK to users.id: who wrote it.';
COMMENT ON COLUMN comments.body       IS 'Comment text, up to 4000 chars.';
COMMENT ON COLUMN comments.created_at IS 'Posting time (UTC).';

-- ================================================================ payments
COMMENT ON TABLE  payout_batches IS 'A group of payments released together by Finance (e.g. one bank upload).';
COMMENT ON COLUMN payout_batches.id         IS 'Surrogate primary key.';
COMMENT ON COLUMN payout_batches.created_by IS 'FK to users.id: Finance user who created the batch.';
COMMENT ON COLUMN payout_batches.created_at IS 'Creation time (UTC).';
COMMENT ON COLUMN payout_batches.status     IS 'Batch lifecycle state (e.g. open / completed).';

COMMENT ON TABLE  payments IS 'Actual money transfer for an approved claim. At most one payment per claim.';
COMMENT ON COLUMN payments.id          IS 'Surrogate primary key.';
COMMENT ON COLUMN payments.claim_id    IS 'FK to claims.id, UNIQUE: one payment per claim.';
COMMENT ON COLUMN payments.batch_id    IS 'FK to payout_batches.id. NULL if paid individually.';
COMMENT ON COLUMN payments.amount      IS 'Amount paid, INR, > 0.';
COMMENT ON COLUMN payments.paid_on     IS 'Date the transfer was made.';
COMMENT ON COLUMN payments.mode        IS 'Transfer mode: NEFT, IMPS or UPI.';
COMMENT ON COLUMN payments.utr         IS 'Bank Unique Transaction Reference for reconciliation.';
COMMENT ON COLUMN payments.recorded_by IS 'FK to users.id: Finance user who recorded it.';
COMMENT ON COLUMN payments.recorded_at IS 'When the payment was recorded (UTC).';

-- ================================================================ audit, notifications, settings
COMMENT ON TABLE  audit_log IS 'Append-only history of who did what. UPDATE and DELETE are blocked by trigger trg_audit_log_immutable.';
COMMENT ON COLUMN audit_log.id              IS 'Surrogate primary key.';
COMMENT ON COLUMN audit_log.entity_type     IS 'Kind of record affected, e.g. CLAIM, TEAM, USER.';
COMMENT ON COLUMN audit_log.entity_id       IS 'Id of the affected record (no FK, as it spans several tables).';
COMMENT ON COLUMN audit_log.action          IS 'What happened, e.g. CLAIM_VERIFIED.';
COMMENT ON COLUMN audit_log.actor_id        IS 'FK to users.id: who performed the action.';
COMMENT ON COLUMN audit_log.on_behalf_of_id IS 'FK to users.id: the person the actor was acting for (delegation).';
COMMENT ON COLUMN audit_log.permission_used IS 'permissions.code that authorised the action.';
COMMENT ON COLUMN audit_log.from_state      IS 'Status before the change.';
COMMENT ON COLUMN audit_log.to_state        IS 'Status after the change.';
COMMENT ON COLUMN audit_log.detail          IS 'Free-form JSONB payload with extra context.';
COMMENT ON COLUMN audit_log.created_at      IS 'Event time (UTC).';
COMMENT ON FUNCTION audit_log_immutable()   IS 'Trigger function that raises an exception on any UPDATE/DELETE of audit_log.';

COMMENT ON TABLE  notifications IS 'In-app notifications shown in the bell/inbox.';
COMMENT ON COLUMN notifications.id         IS 'Surrogate primary key.';
COMMENT ON COLUMN notifications.user_id    IS 'FK to users.id: recipient.';
COMMENT ON COLUMN notifications.message    IS 'Notification text.';
COMMENT ON COLUMN notifications.link       IS 'In-app URL to open when clicked.';
COMMENT ON COLUMN notifications.read       IS 'TRUE once the recipient has seen it.';
COMMENT ON COLUMN notifications.created_at IS 'Creation time (UTC).';

COMMENT ON TABLE  settings IS 'Key/value application configuration editable at runtime (budgets, SLAs, thresholds). Seeded in V2.';
COMMENT ON COLUMN settings.key        IS 'Setting name, e.g. sla.normal.working_days. Primary key.';
COMMENT ON COLUMN settings.value      IS 'Setting value stored as text; parsed by the application.';
COMMENT ON COLUMN settings.updated_by IS 'FK to users.id: last editor.';
COMMENT ON COLUMN settings.updated_at IS 'Last change time (UTC).';

-- ================================================================ AI connector / MCP (V3__mcp_oauth.sql)
COMMENT ON TABLE  mcp_clients IS 'AI apps (e.g. ChatGPT, Claude) that self-registered via OAuth Dynamic Client Registration (RFC 7591). Public clients only, PKCE mandatory.';
COMMENT ON COLUMN mcp_clients.client_id     IS 'OAuth client identifier. Primary key.';
COMMENT ON COLUMN mcp_clients.client_name   IS 'Display name of the AI app, shown on the consent screen.';
COMMENT ON COLUMN mcp_clients.redirect_uris IS 'Allowed redirect URIs, newline-separated.';
COMMENT ON COLUMN mcp_clients.created_at    IS 'Registration time (UTC).';

COMMENT ON TABLE  mcp_grants IS 'A user''s consent for one AI client. Revoking a grant invalidates all its tokens.';
COMMENT ON COLUMN mcp_grants.id           IS 'Surrogate primary key.';
COMMENT ON COLUMN mcp_grants.client_id    IS 'FK to mcp_clients.client_id.';
COMMENT ON COLUMN mcp_grants.user_id      IS 'FK to users.id: the student who consented.';
COMMENT ON COLUMN mcp_grants.scope        IS 'OAuth scope(s) granted.';
COMMENT ON COLUMN mcp_grants.created_at   IS 'When consent was given (UTC).';
COMMENT ON COLUMN mcp_grants.last_used_at IS 'Last time a token from this grant was used.';
COMMENT ON COLUMN mcp_grants.revoked_at   IS 'Set when the user revokes access; NULL = active.';

COMMENT ON TABLE  mcp_auth_codes IS 'OAuth authorization codes: single use, 5 minutes, bound to client, redirect URI and PKCE challenge. Only the hash is stored.';
COMMENT ON COLUMN mcp_auth_codes.code_hash      IS 'SHA-256 of the code. Primary key.';
COMMENT ON COLUMN mcp_auth_codes.grant_id       IS 'FK to mcp_grants.id.';
COMMENT ON COLUMN mcp_auth_codes.redirect_uri   IS 'Redirect URI the code was issued for; must match on exchange.';
COMMENT ON COLUMN mcp_auth_codes.code_challenge IS 'PKCE code challenge.';
COMMENT ON COLUMN mcp_auth_codes.expires_at     IS 'Code is invalid after this instant.';
COMMENT ON COLUMN mcp_auth_codes.used_at        IS 'Set when exchanged; NULL = unused.';

COMMENT ON TABLE  mcp_tokens IS 'Access tokens (1 hour) and refresh tokens (30 days, rotated on every use). Only the hash is stored.';
COMMENT ON COLUMN mcp_tokens.token_hash IS 'SHA-256 of the token. Primary key.';
COMMENT ON COLUMN mcp_tokens.grant_id   IS 'FK to mcp_grants.id.';
COMMENT ON COLUMN mcp_tokens.kind       IS 'ACCESS or REFRESH.';
COMMENT ON COLUMN mcp_tokens.expires_at IS 'Token is invalid after this instant.';
COMMENT ON COLUMN mcp_tokens.revoked_at IS 'Set when revoked or rotated; NULL = valid.';
COMMENT ON COLUMN mcp_tokens.created_at IS 'Issue time (UTC).';

COMMENT ON TABLE  upload_links IS 'Time-limited links handed out by the AI so the student can attach the real file to a claim in a browser. Only the hash is stored.';
COMMENT ON COLUMN upload_links.token_hash IS 'SHA-256 of the link token. Primary key.';
COMMENT ON COLUMN upload_links.claim_id   IS 'FK to claims.id: claim the file will attach to.';
COMMENT ON COLUMN upload_links.user_id    IS 'FK to users.id: the student allowed to upload.';
COMMENT ON COLUMN upload_links.expires_at IS 'Link is invalid after this instant.';
COMMENT ON COLUMN upload_links.created_at IS 'Creation time (UTC).';

-- ================================================================ duplicate protection (V4)
COMMENT ON INDEX ux_claims_vendor_invoice IS 'An invoice can be reimbursed only once across all teams: unique on normalised (vendor_name, invoice_number), letters/digits only, upper case. Mirrors DuplicateKeys.of in Java.';
