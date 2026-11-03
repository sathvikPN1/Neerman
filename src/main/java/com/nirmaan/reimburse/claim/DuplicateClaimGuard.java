package com.nirmaan.reimburse.claim;

import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Stops the same expense being reimbursed twice.
 * <ul>
 *   <li>Same vendor + invoice number as any other claim, from any team and in any status: refused.
 *       The unique index {@link #INVOICE_INDEX} backs this up if two requests race.</li>
 *   <li>Same team, vendor, amount and expense date (for receipts without an invoice number): a warning,
 *       because two identical purchases on one day can be genuine. The verifier decides.</li>
 * </ul>
 * Identical files are refused separately in {@code DocumentService.upload}.
 */
@Component
public class DuplicateClaimGuard {

    public static final String INVOICE_INDEX = "ux_claims_vendor_invoice";

    private static final Logger log = LoggerFactory.getLogger(DuplicateClaimGuard.class);

    private final ClaimRepository claims;

    public DuplicateClaimGuard(ClaimRepository claims) {
        this.claims = claims;
    }

    /**
     * Refuses an invoice that is already on another claim. Matches on the invoice number plus either the vendor
     * name or the GSTIN. Called before the claim is changed, so {@code claimId} is null for a new claim.
     */
    public void requireUniqueInvoice(Long claimId, Long teamId, ClaimForm form) {
        String invoiceKey = DuplicateKeys.of(form.invoiceNumber());
        if (invoiceKey == null) {
            return;
        }
        String vendorKey = Objects.requireNonNullElse(DuplicateKeys.of(form.vendorName()), "");
        String gstin = form.vendorGstin() == null ? "" : form.vendorGstin().trim();
        claims.findSameInvoice(claimId == null ? -1L : claimId, invoiceKey, vendorKey, gstin).ifPresent(other -> {
            if (!other.getTeamId().equals(teamId)) {
                // Don't reveal another team's claim; staff can find it in the audit log or by searching.
                log.warn("Team {} tried to claim invoice {} already on {} (team {})",
                        teamId, form.invoiceNumber(), other.getPublicCode(), other.getTeamId());
                throw new BusinessRuleException("This invoice has already been claimed by another team. "
                        + "Each invoice can be reimbursed only once. If you think this is wrong, contact Nirmaan staff.");
            }
            throw new BusinessRuleException("Invoice " + form.invoiceNumber().trim() + " from " + form.vendorName().trim()
                    + " is already on claim " + other.getPublicCode() + " (" + other.getStatus().label() + "). "
                    + "Each invoice can be reimbursed only once." + hint(other.getStatus()));
        });
    }

    /** Non-blocking warnings for other claims by the same team that look like the same expense. */
    public List<ClaimWarning> possibleDuplicates(Claim claim) {
        String vendorKey = DuplicateKeys.of(claim.getVendorName());
        return claims.findByTeamIdAndClaimedAmountAndExpenseDateAndIdNot(claim.getTeamId(), claim.getClaimedAmount(),
                        claim.getExpenseDate(), claim.getId()).stream()
                .filter(other -> other.getStatus() != ClaimStatus.REJECTED)
                .filter(other -> Objects.equals(DuplicateKeys.of(other.getVendorName()), vendorKey))
                .map(other -> new ClaimWarning("POSSIBLE_DUPLICATE", "Possible duplicate of " + other.getPublicCode()
                        + ": same vendor, amount and expense date. Check this is not the same expense claimed twice.",
                        false))
                .toList();
    }

    private static String hint(ClaimStatus status) {
        return switch (status) {
            case DRAFT, RETURNED -> " Edit that claim instead.";
            case REJECTED -> " That claim was rejected; ask Nirmaan staff to reopen it rather than claiming again.";
            default -> "";
        };
    }
}
// comonets clearly data base schama normalization,edm for db 
