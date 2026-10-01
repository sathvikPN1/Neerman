-- Reference data. Keep `permissions` in sync with com.nirmaan.reimburse.user.Permission
-- (checked at start-up by PermissionCatalogCheck).

INSERT INTO permissions (code, description) VALUES
    ('CLAIM_VERIFY',        'Verify claims and forward them to the COO'),
    ('CLAIM_RETURN_REJECT', 'Send claims back to the team or reject them'),
    ('CLAIM_APPROVE',       'Approve claims on the COO''s behalf'),
    ('CLAIM_FLAG_PRIORITY', 'Mark claims or teams as priority'),
    ('PREAPPROVAL_DECIDE',  'Approve or reject pre-approval requests'),
    ('TEAM_MANAGE',         'Create and edit teams and members, set budgets, promote Pratham to Akshar'),
    ('COHORT_MANAGE',       'Create and edit cohorts'),
    ('BANK_DETAILS_VIEW',   'See full bank account numbers'),
    ('BANK_DETAILS_VERIFY', 'Verify team bank details'),
    ('PAYMENT_RECORD',      'Mark claims paid, create payout batches'),
    ('REPORT_VIEW_EXPORT',  'View analytics, export reports'),
    ('AUDIT_LOG_VIEW',      'View the global audit log'),
    ('CATEGORY_MANAGE',     'Edit expense categories and rules'),
    ('SETTINGS_MANAGE',     'Edit thresholds, SLAs, email templates'),
    ('STAFF_MANAGE',        'Invite staff and edit their permissions (COO only, cannot be granted)');

INSERT INTO role_default_permissions (role, permission_code) VALUES
    ('NIRMAAN_STAFF', 'CLAIM_VERIFY'),
    ('NIRMAAN_STAFF', 'CLAIM_RETURN_REJECT'),
    ('NIRMAAN_STAFF', 'CLAIM_FLAG_PRIORITY'),
    ('NIRMAAN_STAFF', 'PREAPPROVAL_DECIDE'),
    ('NIRMAAN_STAFF', 'TEAM_MANAGE'),
    ('NIRMAAN_STAFF', 'COHORT_MANAGE'),
    ('FINANCE', 'PAYMENT_RECORD'),
    ('FINANCE', 'BANK_DETAILS_VIEW'),
    ('FINANCE', 'REPORT_VIEW_EXPORT');

INSERT INTO categories (name, allowed, requires_pre_approval, pre_approval_recommended, justification_required, description, sort_order) VALUES
    ('Consumables & prototype parts', TRUE,  FALSE, FALSE, FALSE,
     'Parts and consumables that become part of the product or prototype.', 10),
    ('Market research', TRUE, FALSE, FALSE, FALSE,
     'Surveys, user interviews, field visits and data purchases for market validation.', 20),
    ('Subscriptions & software', TRUE, FALSE, TRUE, FALSE,
     'Subscriptions or software used for product development. Pre-approval is recommended.', 30),
    ('Travel for the startup', TRUE, TRUE, FALSE, FALSE,
     'Travel or trips that benefit the startup. Requires an approved pre-approval before you claim.', 40),
    ('Other', TRUE, FALSE, FALSE, TRUE,
     'Anything else that directly benefits the product. Explain clearly in the justification.', 50),
    ('Hiring interns', FALSE, FALSE, FALSE, FALSE,
     'Intern stipends and hiring costs are not reimbursable under the Nirmaan guide.', 100),
    ('Tools not part of the product', FALSE, FALSE, FALSE, FALSE,
     'Tools used to build the prototype that do not become part of the product (e.g. soldering stations, drills) are not reimbursable.', 110),
    ('Courses & training', FALSE, FALSE, FALSE, FALSE,
     'Courses, skill development and training are not reimbursable.', 120),
    ('Marketing & promotion', FALSE, FALSE, FALSE, FALSE,
     'Marketing and promotion expenses are not reimbursable.', 130),
    ('External events & stalls', FALSE, FALSE, FALSE, FALSE,
     'Participation fees for external events or stalls are not reimbursable.', 140),
    ('Accommodation', FALSE, FALSE, FALSE, FALSE,
     'Accommodation is not reimbursable.', 150);

INSERT INTO settings (key, value) VALUES
    ('budget.default.pratham',        '200000.00'),
    ('budget.default.akshar',         '500000.00'),
    ('sla.normal.working_days',       '5'),
    ('sla.priority.working_days',     '2'),
    ('auto_approve.threshold',        '0.00'),
    ('preapproval.tolerance_percent', '10'),
    ('digest.time',                   '09:00');
