-- An invoice can be reimbursed only once, across all teams and all statuses.
-- Vendor name and invoice number are compared as letters and digits only, upper case
-- (the same rule as DuplicateKeys.of in Java), so "INV-0042" and "inv 0042" collide.
-- DuplicateClaimGuard checks first and gives a clear message; this index stops two
-- simultaneous requests from both getting through.
CREATE UNIQUE INDEX ux_claims_vendor_invoice ON claims (
    upper(regexp_replace(vendor_name, '[^A-Za-z0-9]', '', 'g')),
    upper(regexp_replace(invoice_number, '[^A-Za-z0-9]', '', 'g'))
)
WHERE invoice_number IS NOT NULL
  AND regexp_replace(invoice_number, '[^A-Za-z0-9]', '', 'g') <> '';
