# Nirmaan Reimbursements: UI brief and screen inventory

Brief for redesigning the whole interface. It lists every screen in the app as built today (Phase 1): who uses it, what it shows, every element and action on it, and the states it must handle. Screens planned for later phases are listed at the end.

---

## 1. Product context

**What it is.** Nirmaan, the pre-incubator at IIT Madras, funds student startup teams. Teams spend their own money on approved expenses and then claim reimbursement. The app replaces an email process:

> team submits a claim → Nirmaan staff verify it → the COO approves it → Finance pays it

Every step is tracked, audit-logged and visible to the people involved. Urgent claims get priority handling.

**Users**

| Role | Who | Main screen | Device |
|---|---|---|---|
| Team member | Student founders (2–4 per team) | Team dashboard, claim form | Mostly phone, sometimes laptop |
| Nirmaan staff | Program staff who check claims | Verification queue, claim detail | Laptop (needs room for documents next to data) |
| COO | Chief Operating Officer; approves everything; very busy | Approvals | **Phone first**: approves in seconds between meetings |
| Finance | Finance team who pay approved claims | Payment queue | Laptop |

Back-office access is permission-based. A staff member only sees a screen or button if they hold its permission. The COO holds every permission.

**Design priorities**
1. **COO speed.** The COO should be able to approve a claim in one tap on a phone.
2. **Clarity of status.** Anyone can see where a claim is and what's needed next.
3. **Trust.** Money amounts, budgets and reasons are always visible and never ambiguous.
4. **Mobile-first and accessible.** Labels on every input, keyboard focus visible, WCAG AA contrast.

---

## 2. Technical constraints the design must respect

- **Server-rendered HTML.** Spring Boot + Thymeleaf templates, **Tailwind CSS** for styling, **HTMX** for small partial updates. There is **no React or SPA**, so designs should be buildable as plain HTML + Tailwind classes.
- **Forms are real HTML forms.** They submit with a page reload, then show a success or error message at the top of the page (a "flash" message). Only these use HTMX (in-place updates without reloading):
  - live warnings on the claim form, as the user types the amount or picks a category;
  - one-tap decisions on the COO approvals list; the row is replaced by its result;
  - posting a message in a claim's thread;
  - the notification badge, which refreshes every 60 seconds.
- **Expand and collapse use native `<details>` / `<summary>`.** No custom JavaScript components; keep interactions simple.
- **Confirmations use the browser's `confirm()` dialog** today (delete draft, deactivate user, transfer COO, bulk approve). A designed confirmation pattern is welcome, but it must work without heavy JavaScript.
- **Formats**
  - Money is always Indian format with the rupee sign: **₹1,25,000.00**.
  - Dates are `dd MMM yyyy` (e.g. `08 Oct 2026`); date and time is `08 Oct 2026, 11:30 AM` (IST).
  - Ages are short: `3d`, `5h`, `12m`.
  - Claim IDs look like **`NRM-2026-00042`**.

---

## 3. Brand

Taken from nirmaan.iitm.ac.in and the Nirmaan logo (white lotus-like mark on a green rounded square with "NIRMAAN" in black).

| Token | Hex | Use |
|---|---|---|
| brand-500 (logo green) | `#66BB6A` | Accents: top bar, focus rings, highlights, illustrations. **Not for text on white** (too light). |
| brand-600 | `#4C9C50` | Hover states, secondary accents |
| brand-400 | `#83D687` | Light accents |
| brand-700 | `#2E7D32` | **Primary buttons and links** (white text on it passes WCAG AA, about 5.1:1) |
| brand-800 | `#1B5E20` | Primary hover, dark text accents |
| brand-50 / 100 | `#F1F8F1` / `#DCEFDD` | Tinted backgrounds, selected rows, "my message" bubbles |
| ink | `#0B0B0B` | Main text |
| page | `#F8F9FA` | Page background |
| IITM navy (optional) | `#002855` | Secondary accent, used on the Nirmaan site |

Logo file: `src/main/resources/static/img/nirmaan-logo.png` (256 px). The favicon is `favicon.png`.

**Status colours** must stay distinct from the brand green, so "approved" never looks like a plain button. The current mapping:

| Status | Meaning | Current chip |
|---|---|---|
| Draft | Team still editing | Grey |
| Submitted | Waiting for staff | Sky blue |
| Verified | Waiting for the COO | Violet/indigo |
| Returned | Sent back to the team for changes | Amber |
| Approved | Approved, waiting for payment | Emerald (light) |
| Paid | Money sent | Solid green, white text |
| Rejected | Final no | Rose |

Other flags:
- **Priority:** solid red (rose-600) chip with a flag icon, text "Priority".
- **Priority requested:** amber chip.
- **Over budget:** red text.
- **Partial approval:** amber chip "Partial".

---

## 4. Global components (used on many screens)

### 4.1 App header (every signed-in page)
- Logo plus the wordmark "Nirmaan **Reimbursements**".
- Right side:
  - notification bell with a red unread-count badge (shows "99+" above 99; refreshes every 60 s);
  - the user's name (hidden on small screens);
  - "Sign out" button.
- Main navigation, a horizontal row that scrolls sideways on mobile. Each item appears only if the user may use it:

| Item | Shown to |
|---|---|
| My team | Team members |
| New claim | Team members |
| Approvals | Anyone with `CLAIM_APPROVE` (COO, delegates) |
| Verification queue | Anyone with a verify / return-reject / approve / priority permission |
| Payments | `PAYMENT_RECORD` (Finance) |
| Teams | All back-office users |
| Cohorts | All back-office users |
| Categories | Everyone, including teams, who see it as the rules page |
| Audit log | `AUDIT_LOG_VIEW` |
| Staff & permissions | COO only |

- "Skip to content" link for keyboard users.
- Should indicate the current page (not done today).

### 4.2 Flash messages
- Success (green) and error (red) banners at the top of the page content after any form action.
- Examples:
  - "Claim verified and forwarded for approval."
  - "Approving ₹6,000.00 would exceed the team's remaining budget of ₹3,000.00. Partially approve up to ₹3,000.00 instead."

### 4.3 Inline error (HTMX)
A small red box that replaces the content of the area that was being updated, used when a one-tap action fails.

### 4.4 Status chip and priority badge
See section 3.

### 4.5 Budget meter
- Card titled "Your budget" (team view) or "Team budget" (staff view), with "Total ₹2,00,000.00" on the right.
- A horizontal stacked bar:
  - **Paid** (dark green)
  - **Approved, unpaid** (light green)
  - **Pending review** (amber)
  - the rest is remaining (grey)
- A legend of 4 figures below the bar: Paid, Approved unpaid, Pending review, Remaining. Remaining turns red if negative.
- Budgets: Pratham teams ₹2,00,000; Akshar teams ₹5,00,000.

### 4.6 Buttons
- Primary (brand green)
- Success (approve / pay)
- Danger (reject / deactivate)
- Warning (return)
- Ghost (secondary)

All are at least 40 px tall for touch.

### 4.7 Form fields
- Label above the field.
- Hint text below in small grey.
- Field error in red below.
- Inputs are full width on mobile.

### 4.8 Empty states
Every list has one, with a friendly line and, where possible, the next action. Examples:
- "No claims yet." with a "Create your first claim" button
- "Nothing waiting here. 🎉"
- "All caught up. 🎉"
- "You're all caught up."

### 4.9 Card
White background, light border, rounded corners. This is the basic container on every screen.

---

## 5. Screens

Format for each screen: **route** · **who sees it** · purpose → contents → actions → states → layout notes.

### Screen 1: Sign in
- **Route:** `/login` · **Who:** everyone (public)
- **Purpose:** sign in with email and password.
- **Contents**
  - Logo (large), title "Nirmaan Reimbursements", subtitle "Pre-incubator, IIT Madras".
  - Card with:
    - Email field
    - Password field
    - "Sign in" button
- **Messages shown above the form**, depending on how the user arrived:
  - Wrong email or password, or account not active (error)
  - Too many failed attempts, try again in 15 minutes (warning; after 5 failures)
  - "You have signed out." (neutral)
  - "Your password is set. Sign in to continue." (success; after accepting an invite)
  - "Your account is not active." (error; user was deactivated while signed in)
- **Notes:** centred single column. There is no self-registration and no "forgot password" today; users are invited. A future "Sign in with IITM" SSO button is possible.

### Screen 2: Set your password (accept invite)
- **Route:** `/invite/{token}` · **Who:** a newly invited user, from the email link (public)
- **Contents:** title "Set your password", New password field (min 10 characters, with hint), Confirm password field, "Activate account" button.
- **States**
  - Invalid, used or expired link (7-day validity): error box "This invitation link is invalid, already used or expired. Ask Nirmaan to send a new one." and no form.
  - Validation errors: passwords don't match; too short.
- **After success:** goes to sign in with the "password is set" message.

### Screen 3: Error page
- **Who:** anyone
- **Contents**
  - Large status number (403 / 404 / other).
  - Title: "Not allowed" / "Not found" / "Something went wrong".
  - Message, e.g. "You don't have permission to do that.", "Claim 42 was not found", or a specific rule such as "You verified this claim, so someone else must approve it."
  - "Go to my home page" button.
- The header and navigation are shown when signed in.

### Screen 4: Team dashboard
- **Route:** `/team` · **Who:** team members (their own team only)
- **Purpose:** the team's home: budget, claims and what's next.
- **Contents**
  1. Team name (heading), "Program · Cohort" (e.g. "Pratham · Pratham Jul 2026"), and a **"+ New claim"** primary button.
  2. **Budget meter** (component 4.5).
  3. Info line: "Nirmaan funding is seed money, not a grant: spend on approved items first, then claim reimbursement here with the invoice and payment proof." plus a link "What can I claim?" (goes to Categories).
  4. **Claims list.** Each row shows:
     - vendor name, category;
     - claim ID, expense date;
     - amount (the approved amount if approved, otherwise the claimed amount);
     - status chip, and a priority badge if the claim is priority.

     Tapping a row opens the claim's edit page if it's Draft or Returned, otherwise its detail page.
  5. **Team members:** a simple list of name and email.
- **States**
  - No claims: empty state with "Create your first claim".
  - User not in a team: error page "You are not part of a team yet. Ask Nirmaan staff to add you."
- **Missing today, useful to design:** a summary of what needs the team's attention (e.g. "1 claim returned — fix it"), and status filter tabs.

### Screen 5: Claim form (new / edit draft / fix returned claim)
- **Routes:** `/claims/new`, `/claims/{id}/edit` · **Who:** team members of the claim's team, while the claim is Draft or Returned
- **Purpose:** enter the claim details, upload documents, submit.
- **Header:** "New claim", or "Claim NRM-2026-00042" plus a status chip.
- **Returned banner** (amber), if returned: "Returned for changes: *reason from staff*" with "Fix the details or documents below, then resubmit."
- **Budget line:** "Remaining budget: ₹1,81,550.00 (₹5,000.00 pending review)".
- **Live warnings area.** It updates while typing the amount or changing the category.
  - **Blocking (red), prefixed "Cannot submit:"**
    - category not reimbursable (shows the rule text);
    - category requires an approved pre-approval (Travel).
  - **Notes (amber), prefixed "Note:"**
    - pre-approval recommended (Software);
    - detailed justification needed ("Other", at least 30 characters);
    - over remaining budget ("The COO may approve it only partially").
- **Fields**

| Field | Type | Rules / hint |
|---|---|---|
| Category | Select, grouped **Reimbursable** / **Not reimbursable** | Pre-approval categories are labelled "(pre-approval required)" |
| Amount (₹) | Decimal | Required, at least ₹1 |
| Expense date | Date | Required, not in the future |
| Vendor / seller | Text | Required |
| Vendor GSTIN | Text, uppercase, optional | Format check, e.g. `33ABCDE1234F1Z5` |
| Invoice number | Text | Optional |
| Invoice date | Date | Optional |
| How does this expense benefit your project? | Textarea, required | Hint: "Required by the Nirmaan guide. Be specific: what you bought and how it moves the product forward." |

- **Buttons:** "Save draft & add documents" (new) or "Save changes" (edit); "Cancel".
- **Documents section** (only once the draft is saved)
  - Explanation: upload the **invoice** (a real tax invoice; pro-forma invoices are not accepted) and the **payment proof** (UPI screenshot, bank statement extract or card slip). PDF, JPG, PNG or HEIC, up to 10 MB each.
  - Uploaded list: each row shows the filename (opens the file), "type · size" and a "Remove" button.
  - Upload row: document type select (Invoice / Payment proof / Other attachment), file picker, "Upload" button.
  - Errors: wrong file type ("Only PDF, JPG, PNG or HEIC files are accepted."), too large, empty file.
- **Footer actions**
  - **"Submit claim"**, or **"Resubmit claim"** if it was returned.
  - "Delete draft" (only for drafts that were never submitted; asks for confirmation).
  - Link "View history & messages".
- **Submit errors** appear as a flash message, e.g. "Upload the invoice before submitting…", "Upload the payment proof…", or a category rule.
- **Design opportunity:** today this is one long page. A clearer flow would be *details → documents → review & submit*, with a checklist showing what's still missing.

### Screen 6: Claim detail (the central screen; content depends on role)
- **Route:** `/claims/{id}` · **Who:** the claim's team members; all back-office users. Staff use it as the **verification screen**.
- **Header**
  - Claim ID (small), "Vendor · ₹amount" (heading).
  - "Team · Category" (the team name links to the team page for staff).
  - Chips: status, Priority (claim or team), "Priority requested".
- **Banners**
  - Decision reason: red if rejected; amber if returned; "Approval note" if approved or paid with a note.
  - For the team when the claim is Draft or Returned: "This claim was returned. Edit it and resubmit." plus an **"Edit & submit"** button.
- **Layout:** two columns on desktop (left: data and actions; right: documents). They stack on mobile.
- **Left column**
  1. **Details card**
     - Claimed, Approved (with a "Partial" chip when lower).
     - Expense date, Vendor GSTIN, Invoice no., Invoice date.
     - Submitted by, Submitted (date and time), Verified by, Approved by.
     - Justification (full text).
     - Priority reason; Team's priority request.
     - **Paid box** (green), once paid: "Paid ₹X on date via NEFT · UTR XXXX".
  2. **Team budget meter**, staff only.
  3. **Warnings**, the same as on the form.
  4. **Actions card.** Only the actions the current user may perform right now are shown:

| Action | Who / when | UI |
|---|---|---|
| Verify & forward for approval | Staff with `CLAIM_VERIFY`, claim Submitted | Primary button |
| Approve ₹X / Verify & approve ₹X | Approvers when Verified; COO also when Submitted | Success button |
| Partially approve… | Approvers | Expandable: amount (pre-filled with the remaining budget if over), reason (shared with the team), button |
| Return to team for changes… | Staff or approvers | Expandable: "What should the team fix?" textarea and button |
| Reject… | Staff or approvers | Expandable: reason textarea and danger button |
| COO override: reopen… | COO, claim Rejected or Approved (not Paid) | Expandable: reason (audit-logged) |
| Mark as paid | Finance, claim Approved | Inline form: paid on (date, defaults to today), mode NEFT/IMPS/UPI, UTR/reference, button "Mark as paid ₹X" |

     A staff member who verified the claim does not see Approve (four-eyes rule); the COO is the exception.
  5. **Priority card**
     - **Staff:** "Accept priority request" / "Decline request" (with an optional note to the team), when the team asked; "Mark as priority" (reason required, e.g. "student cannot afford to wait"); "Remove priority".
     - **Team:** "Need this paid urgently? Tell Nirmaan why." textarea plus "Request priority", while the claim is in progress and not already priority or requested.
- **Right column: documents.** One card per document:
  - header "Type · filename" plus a "Download" link;
  - **inline preview**: PDF in an embedded viewer, images shown directly, HEIC shows "Preview not available, download to view";
  - a small grey SHA-256 fingerprint line.

  When nothing is uploaded: "No documents uploaded yet."
- **Below: two cards side by side**
  - **Messages** (the comment thread that replaces email)
    - Chat-style bubbles: my messages on the right (green tint), others on the left (grey).
    - Each bubble shows the author, their role (e.g. "Nirmaan staff"), the date and time, and the text.
    - A new-message textarea and "Send" button. It posts without a page reload and the thread refreshes.
    - Empty: "No messages yet. Ask a question or add context here instead of email."
  - **History**: a vertical timeline. Each event shows:
    - the action (e.g. "Submitted", "Verified", "Partially approved", "Returned", "Paid", "Priority set", "Document uploaded");
    - "by Name (on behalf of COO)" when relevant;
    - the date and time;
    - the quoted comment, if any.
- **Design opportunity:** a status stepper at the top (Draft → Submitted → Verified → Approved → Paid) showing the current step and who it's waiting on. A sticky action bar on mobile.

### Screen 7: Verification queue (staff)
- **Route:** `/staff/queue` · **Who:** staff with a verify, return/reject, approve or priority permission; the COO
- **Purpose:** work through submitted claims, priority first, then oldest first.
- **Filter bar** (Apply button; filters are kept in the URL):

| Filter | Options |
|---|---|
| Status | Submitted (default), or any status |
| Team | All, or a team |
| Cohort | All, or a cohort |
| Category | All, or a category |
| Waiting at least | Any time / 2 / 5 / 10 days |

- **Table columns**
  - **Claim:** ID link, vendor, and Priority / Priority requested chips.
  - **Team:** name and cohort.
  - **Category.**
  - **Amount**, right-aligned.
  - **Waiting:** age in the current stage.
  - **Status** chip.

  Priority rows have a light red tint. Clicking the claim ID opens Screen 6.
- **Empty:** "Nothing waiting here. 🎉 New submissions will appear automatically, priority claims first."
- **Design opportunity:** a card list on mobile instead of a table; count badges per status; highlighting claims over their target time (target: 2 working days for priority claims, 5 for normal).

### Screen 8: Approvals (COO; mobile-first, the most important screen)
- **Route:** `/coo/approvals` · **Who:** COO and anyone with `CLAIM_APPROVE` (delegates)
- **Purpose:** approve verified claims in seconds, on a phone.
- **Top:** "Approvals", "N waiting · ₹total", and a link "Full staff queue →".
- **Sticky bulk bar:** "Select all" checkbox and an **"Approve selected"** button (asks for confirmation; approves each selected claim in full).
- **Sections**
  - **"Priority"** (red heading) first.
  - **"Everything else"** after it.
- **Collapsed row** (one line, tappable):
  - checkbox (disabled if the user verified it themselves or it's over budget);
  - **Team · Category**;
  - small line: age · "verified by Name" · red "over budget" when relevant;
  - **amount** on the right, and the Priority badge.
- **Expanded row**
  - Justification text.
  - Priority reason (red text).
  - **Invoice thumbnail** (image) or "📄 Open invoice (PDF)" tile, which opens the document.
  - Budget impact: Budget, Remaining now, After approval (red if negative).
  - If the user verified it: "You verified this claim, so someone else must approve it." and no actions.
  - Actions:
    - **Approve** (one tap; the row turns into "NRM-… — Approved ✓" without a page reload);
    - **Open** (full detail);
    - **Partial:** amount field (pre-filled with the remaining budget when over) plus a reason, then "Approve partially";
    - **Return:** reason plus "Return";
    - **Reject:** reason plus "Reject".
- **Footer note:** "Rows you verified yourself or that exceed the team budget can't be bulk-approved; open them to decide."
- **Empty:** "All caught up. 🎉 Verified claims will appear here, priority first."
- **Design opportunity:** swipe or large tap targets, a thumb-reachable bottom action bar, quick reason chips for return/reject (e.g. "Invoice is pro-forma", "Missing GSTIN").

### Screen 9: Payment queue (Finance)
- **Route:** `/finance/payments` · **Who:** `PAYMENT_RECORD` (Finance; COO)
- **Purpose:** pay approved claims and record the transaction reference.
- **Top:** title and the note "Approved claims waiting for payment, priority first. Bank details and batch payouts arrive in the next release."
- **One card per claim**
  - Claim ID link, Priority badge.
  - "Team · Vendor · approved Xd ago".
  - **Approved amount** (large).
  - Inline form: Paid on (date, defaults to today, not in the future); Mode (NEFT / IMPS / UPI); **UTR / reference** (required, letters, digits and dashes); **"Mark as paid"**.
- **Empty:** "No approved claims waiting for payment."
- **Phase 2 additions to design for:** the team's bank details (masked account number, "show full" logged), multi-select, and "Export batch" (Excel/CSV) / "Mark batch paid".

### Screen 10: Teams list
- **Route:** `/teams` · **Who:** all back-office users. The "+ New team" button needs `TEAM_MANAGE`.
- **Table columns**
  - **Team:** name link, Priority badge, "cohort · N member(s)".
  - **Program:** Pratham / Akshar.
  - **Budget.**
  - **Approved + paid.**
  - **Pending.**
  - **Remaining** (bold).
- **Empty:** "No teams yet. Create a cohort first, then add teams."
- **Design opportunity:** a small budget-used bar per row; filter by cohort or program; search.

### Screen 11: Team detail
- **Route:** `/teams/{id}` · **Who:** back-office users
- **Header**
  - Team name and Priority badge.
  - "Program · Cohort".
  - Priority reason (if any).
  - "Edit team & budget" button (`TEAM_MANAGE`).
- **Budget meter.**
- **Members card**
  - List: name, email, "Invite pending" / "Deactivated" chips, and "Remove" (asks for confirmation; deactivates the account).
  - Add-member form: Name, Email, "Add & invite" (sends an invite email).
- **Team priority card** (`CLAIM_FLAG_PRIORITY`)
  - Explanation: "All of this team's claims go to the top of every queue with a shorter target time."
  - Reason field plus "Mark team as priority", or "Remove team priority".
- **Claims list:** ID, vendor, category, amount, status chip; each opens the claim.

### Screen 12: New / edit team
- **Routes:** `/teams/new`, `/teams/{id}/edit` · **Who:** `TEAM_MANAGE`
- **Fields**
  - Team name.
  - Cohort (select).
  - Program (Pratham / Akshar), with the hint that changing Pratham → Akshar updates the budget and is audit-logged.
  - Budget (₹), optional, with the hint "Leave empty for the program default: Pratham ₹2,00,000.00, Akshar ₹5,00,000.00".
- **Warning** when no cohort exists yet: "Create a cohort first." with a link.
- **Button:** "Save team".

### Screen 13: Cohorts
- **Route:** `/cohorts` · **Who:** back-office users; create and edit need `COHORT_MANAGE`
- **Create form:** name (e.g. "Pratham Jan 2027"), start date, end date, "Create cohort".
- **List:** each cohort is an expandable row: name, "start – end · N team(s)". Expanding shows an edit form (name, start, end, Save).
- **Empty:** "No cohorts yet."

### Screen 14: Expense categories (also the teams' rules page)
- **Route:** `/categories` · **Who:** everyone can read; `CATEGORY_MANAGE` can edit
- **Intro:** "From the Nirmaan Student Guide. Every claim needs a real invoice (not pro-forma), a payment proof and a justification of how it benefits your project."
- **One card per category**
  - Name, with chips: Reimbursable / Not reimbursable / Pre-approval required / Pre-approval recommended / Detailed justification / Inactive.
  - The rule text.
- **Managers also see**
  - "Edit" (expands an inline form).
  - An "Add a category" card. Its form fields: name; description shown to teams; checkboxes for reimbursable, requires pre-approval, pre-approval recommended, detailed justification and active; sort order.
- **Current data**
  - Reimbursable: Consumables & prototype parts, Market research, Subscriptions & software (recommended), Travel (pre-approval required), Other (detailed justification).
  - Not reimbursable: Hiring interns, Tools not part of the product, Courses & training, Marketing & promotion, External events & stalls, Accommodation.
- **Design opportunity:** for teams, show this as a clear "Can I claim this?" page with allowed and not-allowed groups.

### Screen 15: Staff & permissions (COO only)
- **Route:** `/admin/staff` · **Who:** COO only
- **Invite card:** Name, Email, Role (Nirmaan staff / Finance), "Send invite", and the hint "They get the role's default permissions, which you can then adjust per person."
- **People table** (COO first, then staff, then Finance)
  - **Name:** with email, plus "Invite pending" / "Deactivated" chips.
  - **Role.**
  - **Permissions:** chips, e.g. `CLAIM_VERIFY`, with "(until 31 Oct 2026)" when time-limited; the COO shows "All permissions".
  - **Last active:** date and time, or "Never".
  - **Actions:** "Edit" (not for the COO) and "Resend invite" when pending.
- **Default permission bundles:** two forms (Nirmaan staff, Finance), each a checkbox grid of the 14 grantable permissions plus "Save defaults". Hint: "Applied when a new user is invited. Existing users keep their own permissions."
- **Transfer the COO role** (danger zone)
  - Explanation that the COO becomes Nirmaan staff and can't undo it.
  - Select "New COO" (active, activated staff only).
  - Type **TRANSFER** to confirm.
  - "Transfer COO role" (danger; asks for confirmation).
- **Permission change history:** recent entries, e.g. "Permission granted CLAIM_APPROVE for x@… (expires …) by Anita · 08 Oct 2026, 11:30 AM".

### Screen 16: Edit a staff member's permissions (COO only)
- **Route:** `/admin/staff/{id}`
- **Header:** back link, name, "email · role".
- **Permissions form:** one row per permission:
  - checkbox;
  - the code (e.g. `CLAIM_APPROVE`) and a one-line description (e.g. "Approve claims on the COO's behalf");
  - an optional **last-day date**; the grant expires automatically after that day.

  Hint: "e.g. CLAIM_APPROVE while the COO is on leave. STAFF_MANAGE belongs to the COO only." Button: "Save permissions". Changes apply on the person's next click.
- **Deactivate / Reactivate card:** "They are signed out immediately and can no longer sign in." with a danger button, or "Reactivate".
- **The 14 grantable permissions:** CLAIM_VERIFY, CLAIM_RETURN_REJECT, CLAIM_APPROVE, CLAIM_FLAG_PRIORITY, PREAPPROVAL_DECIDE, TEAM_MANAGE, COHORT_MANAGE, BANK_DETAILS_VIEW, BANK_DETAILS_VERIFY, PAYMENT_RECORD, REPORT_VIEW_EXPORT, AUDIT_LOG_VIEW, CATEGORY_MANAGE, SETTINGS_MANAGE.
- **Design opportunity:** group the permissions (Claims / Teams / Finance / Admin) and show the role's default bundle for comparison.

### Screen 17: Audit log
- **Route:** `/audit` · **Who:** `AUDIT_LOG_VIEW` (COO by default)
- **Filters:** Entity (All, CLAIM, USER, PERMISSION, TEAM, COHORT, CATEGORY, SETTING, PAYMENT), Action (free text, e.g. "APPROVED"), "Filter".
- **Table columns** (50 per page, newest first)
  - **When (IST).**
  - **Who:** plus "on behalf of X", plus the permission used in small monospace.
  - **Action:** code, e.g. `PARTIALLY_APPROVED`.
  - **Entity:** "Claim #12" as a link, or "TEAM #3".
  - **Change:** e.g. "VERIFIED → APPROVED".
  - **Detail:** key/value data such as comment and amounts.
- **Pagination:** "← Newer" / "Older →".
- **Empty:** "No entries."

### Screen 18: Notifications
- **Route:** `/notifications` · **Who:** everyone signed in
- **Contents**
  - Title and "Mark all read" (when there are unread items).
  - List: unread dot (brand colour), message (bold when unread), date and time. Tapping marks it read and opens the related page, usually a claim.
- **Example messages**
  - "NRM-2026-00004 has been paid: ₹64,500.00."
  - "NRM-2026-00007 was returned for changes. Reason: Invoice is pro-forma."
  - "Priya Raman commented on NRM-2026-00001: "Is the GST invoice fine?""
- **Empty:** "You're all caught up."
- **Design opportunity:** a dropdown panel from the bell in the header, with this page as "See all".

---

## 6. Screens planned for later phases (design them now if possible)

| Phase | Screen / element | Contents |
|---|---|---|
| 2 | **Pre-approval request** (team) | Category, estimated amount, purpose & justification, expected date; list of the team's pre-approvals with status |
| 2 | **Pre-approval queue** (staff) | Approve / reject / forward to COO, with reason |
| 2 | **Link a pre-approval** on the claim form | Select an approved pre-approval; warning if the claim exceeds the estimate by more than 10% |
| 2 | **Invoice checklist panel** on claim detail (staff) | 8 checks, each Pass / Fail / Unknown: "Invoice" present and not pro-forma; seller name, address, contact; GSTIN/PAN; buyer details; invoice date; invoice number; line items; total with tax |
| 2 | **Auto pre-fill** on the claim form | When the uploaded PDF invoice has readable text, pre-fill vendor, invoice number, date and amount, with a "Pre-filled from invoice, please check" note |
| 2 | **Duplicate warning** | "Possible duplicate of NRM-… (same invoice number / same file / same amount and date)" with a link |
| 2 | **Bank details** (team; one-time form) | Account holder, account number (masked), IFSC, bank name; "Verified by staff" state; changing them needs staff re-verification |
| 2 | **Batch payout** (Finance) | Multi-select approved claims, export Excel/CSV, mark the batch paid |
| 3 | **Settings** (COO / `SETTINGS_MANAGE`) | Auto-approval threshold (₹), SLA target days (priority / normal), default budgets, digest time, email templates |
| 3 | **Delegation** | Grant CLAIM_APPROVE to a staff member for a date range |
| 3 | **Email approve / reject confirmation page** | Opened from a signed link in the COO's daily email: claim summary plus "Confirm approve" / "Confirm reject". The link is single-use and expires after 72 h |
| 3 | **Daily digest email** (HTML email) | "N claims waiting (M priority), total ₹X", each with a deep link |
| 3 | **Promote Pratham → Akshar** | Confirm the new program and budget; history kept |
| 4 | **Analytics** (`REPORT_VIEW_EXPORT`) | Average turnaround per stage, claims per status, top categories, budget use per team; Excel export |
| 4 | **Global search** | Search box in the header: claim ID, team, vendor or invoice number |

---

## 7. Sample data for mockups

- **People**
  - COO: Anita Subramanian.
  - Staff: Sneha Iyer, Karthik Rao.
  - Finance: Lakshmi Narayanan.
  - Team members: Priya Raman and Arjun Mehta (AgriSense), Meera Krishnan (MedTrack), Rahul Verma (VoltCycle).
- **Cohort:** "Pratham Jul 2026" (01 Jul – 31 Dec 2026).
- **Teams:** AgriSense (Pratham, ₹2,00,000), MedTrack (Pratham, ₹2,00,000), VoltCycle (Akshar, ₹5,00,000).
- **Claims**
  - `NRM-2026-00001` · AgriSense · Robu.in · Consumables & prototype parts · ₹18,450.00 · *"Soil moisture sensors and ESP32 boards for field prototype v2"*
  - `NRM-2026-00002` · AgriSense · SurveyMonkey · Market research · ₹3,200.00 · Draft
  - `NRM-2026-00003` · MedTrack · Autodesk India · Subscriptions & software · ₹12,999.00 · **Priority** ("Student paid from personal savings; cannot wait") · *"Fusion 360 licence for enclosure design of the pill dispenser"*
  - `NRM-2026-00004` · VoltCycle · Li-Ion Cells Pvt Ltd · Consumables & prototype parts · ₹64,500.00 · Approved → Paid via NEFT, UTR `UTR998877`

---

## 8. What to deliver

For each screen in section 5 (and section 6 where possible):
1. A **mobile frame (390 px)** and a **desktop frame (1280 px)**. For the COO Approvals screen, mobile is the main design.
2. Its **states**: normal, empty, error/validation, and role variants (e.g. claim detail as team vs staff vs COO vs Finance).
3. A small **component sheet**: header and navigation, buttons, form fields, status chips, priority badge, budget meter, cards, tables and mobile lists, timeline, chat bubbles, flash and inline messages, empty states.
4. Output as **HTML with Tailwind CSS classes** (or a precise spec), using the brand tokens in section 3, so the designs can be dropped into the Thymeleaf templates.
