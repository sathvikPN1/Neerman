# 6. Test Data

## 6.1 Demo accounts

Password for every account: `Demo@1234`. These exist when `DEMO_DATA=true` (the compose default) or in the `dev` profile.

| Role | Email | Notes |
|---|---|---|
| COO | `coo@nirmaan.local` | All permissions, including `STAFF_MANAGE` |
| Nirmaan staff | `staff@nirmaan.local` | Default staff bundle |
| Nirmaan staff | `staff2@nirmaan.local` | Use as the "second approver" in four-eyes tests |
| Finance | `finance@nirmaan.local` | `PAYMENT_RECORD` |
| Team member (AgriSense, Pratham) | `priya@agrisense.local`, `arjun@agrisense.local` | Two members in one team |
| Team member (MedTrack, Pratham) | `meera@medtrack.local` | Team B for isolation tests |
| Team member (VoltCycle, Akshar) | `rahul@voltcycle.local` | Akshar budget (₹5,00,000) |

## 6.2 Seeded claims

| Team | Claim state | Use it for |
|---|---|---|
| AgriSense | Submitted | Verify / return / reject tests |
| AgriSense | Draft | Edit, delete, upload, submit tests |
| MedTrack | Verified, **priority** | COO approvals, priority order |
| VoltCycle | Approved | Finance payment tests |

Reset: `docker compose down -v && docker compose up --build`.

## 6.3 Boundary data sets

### Claim form

| Field | Valid | Invalid |
|---|---|---|
| Amount | `1.00`, `1.01`, `1250.50`, `9999999999.99` | `0`, `0.99`, `-1`, `12.345`, `10000000000.00`, `abc`, empty |
| Expense date | today (IST), yesterday | tomorrow (IST), `31/02/2026`, empty |
| Vendor | 1 char, 200 chars | empty, spaces only, 201 chars |
| GSTIN | empty, `33ABCDE1234F1Z5`, `29AAAAA0000A1Z5` | `33abcde1234f1z5`, `33ABCDE1234F1Z`, `33ABCDE1234F1Z55`, `33ABCDE1234F0Z5` |
| Invoice number | empty, 60 chars | 61 chars |
| Justification | 1 char (normal category), 30 chars ("Other"), 2000 chars | empty, spaces only, 29 chars ("Other"), 2001 chars |

### Approval

| Case | Claimed | Remaining budget | Approved amount | Expected |
|---|---|---|---|---|
| Full | 10,000.00 | 50,000.00 | blank | Approved, 10,000.00 |
| Partial | 10,000.00 | 50,000.00 | 6,000.00 + reason | `PARTIALLY_APPROVED` |
| Partial, no reason | 10,000.00 | 50,000.00 | 6,000.00 | Refused |
| Zero | 10,000.00 | 50,000.00 | 0 | Refused |
| Above claimed | 10,000.00 | 50,000.00 | 10,000.01 | Refused |
| Exactly remaining | 10,000.00 | 10,000.00 | blank | Approved |
| One paisa over | 10,000.01 | 10,000.00 | blank | Refused, "Partially approve up to ₹10,000.00" |
| Exhausted | 100.00 | 0.00 | blank | Refused (no hint) |

### Payment

| Field | Valid | Invalid |
|---|---|---|
| UTR | `UTR123456789`, `AB-12-cd`, 40 chars | empty, 41 chars, `AB 12`, `AB_12`, `AB#12`, `टेस्ट` |
| Paid on | today, a past date | tomorrow, empty |
| Mode | each `PaymentMode` | empty, an unknown value |

### Login

| Case | Input |
|---|---|
| Valid | `priya@agrisense.local` / `Demo@1234` |
| Wrong password | `priya@agrisense.local` / `x` |
| Unknown user | `nobody@nowhere.com` / `x` |
| Case variants | `PRIYA@AGRISENSE.LOCAL` |
| Lock | 5 wrong attempts, then correct |
| Injection | `' OR '1'='1` as email |

## 6.4 Sample files

Create these in a scratch folder (they are not committed). Commands use Git Bash.

| File | How to make it | Expected result |
|---|---|---|
| `ok.pdf` | `printf '%%PDF-1.4\n%%%%EOF' > ok.pdf` (or any real PDF) | Accepted |
| `ok.png` | Any PNG | Accepted |
| `ok.jpg` | Any JPEG | Accepted |
| `ok.heic` | An iPhone photo | Accepted, download only |
| `fake.pdf` | `echo 'MZ...' > fake.pdf` (not a PDF) | Rejected |
| `renamed.txt` | A real PDF named `.txt` | Accepted |
| `script.svg` | `<svg onload=alert(1)>` | Rejected |
| `empty.pdf` | `: > empty.pdf` | Rejected |
| `tiny.pdf` | `printf '%%PDF' > tiny.pdf` (4 bytes, no dash) | Rejected |
| `big_ok.pdf` | `head -c 10485760 /dev/zero` after a `%PDF-` header, to a total of exactly 10,485,760 bytes | Accepted |
| `big_over.pdf` | The same with 10,485,761 bytes | Rejected: "Files must be 10 MB or smaller." |
| `../../evil.pdf` | A valid PDF with this name sent through curl `-F 'file=@ok.pdf;filename=../../evil.pdf'` | Saved safely under a random key |

## 6.5 Time-sensitive data

| Case | Technique |
|---|---|
| Grant expiry | Set the expiry to today; in IT tests use a mutable `Clock` and advance to 00:00 IST the next day |
| Login lock expiry | Advance the clock by 15 min (unit test) |
| IST date edge | Set the clock to 18:29 UTC (23:59 IST) and 18:31 UTC (00:01 IST next day) and check the "future date" rule |
| Invite expiry | Advance the clock past the token lifetime |

## 6.6 Cleanup

- Docker: `docker compose down -v` removes data and uploads.
- Local profile: delete `./data/uploads`.
- Integration tests use Testcontainers, so each run starts from a clean database.
