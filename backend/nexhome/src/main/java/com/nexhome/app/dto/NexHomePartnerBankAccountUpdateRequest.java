package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a bank account. A null (or missing) field keeps its current value; the holder name cannot be blanked and an
 * empty bank name clears it. Changing the holder name sends the account back to PENDING verification.
 * {@code isPrimary: true} makes this the primary account (replacing the current one); {@code false} leaves the partner
 * without one. {@code accountNumber} / {@code ifscCode} may be changed only while the partner profile is not submitted
 * yet (NOT_STARTED, DRAFT, CHANGES_REQUESTED); a change sends the account back to PENDING. The partner cannot be
 * changed: it is declared only so that a request containing it is rejected with a clear message.
 */
public record NexHomePartnerBankAccountUpdateRequest(
		@NotNull(message = "Partner bank account ID is required")
		Long partnerBankAccountId,

		@Null(message = "Partner profile cannot be updated")
		Long partnerProfileId,

		@Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be 9 to 18 digits")
		String accountNumber,

		@Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$",
				message = "IFSC code must be 4 letters, a zero and 6 letters or digits")
		String ifscCode,

		@Pattern(regexp = "(?s).*\\S.*", message = "Account holder name cannot be blank")
		@Size(max = 150, message = "Account holder name must be at most 150 characters")
		String accountHolderName,

		@Size(max = 150, message = "Bank name must be at most 150 characters")
		String bankName,

		@Size(max = 150, message = "Branch must be at most 150 characters")
		String branchName,

		Boolean isPrimary,

		@Null(message = "Verification status cannot be updated here")
		String verificationStatus) {

	/** Never print the account number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerBankAccountUpdateRequest[partnerBankAccountId=" + partnerBankAccountId
				+ ", accountNumber=" + (accountNumber == null ? null : "****") + "]";
	}
}
