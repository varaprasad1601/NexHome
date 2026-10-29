package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a bank account. A null (or missing) field keeps its current value; the holder name cannot be blanked and an
 * empty bank name clears it. Changing the holder name sends the account back to PENDING verification.
 * {@code isPrimary: true} makes this the primary account (replacing the current one); {@code false} leaves the partner
 * without one. The partner, account number and IFSC identify the account and cannot be changed (add a new account):
 * they are only declared here so that a request containing them is rejected with a clear message.
 */
public record NexHomePartnerBankAccountUpdateRequest(
		@NotNull(message = "Partner bank account ID is required")
		Long partnerBankAccountId,

		@Null(message = "Partner profile cannot be updated")
		Long partnerProfileId,

		@Null(message = "Account number cannot be updated; add a new bank account instead")
		String accountNumber,

		@Null(message = "IFSC code cannot be updated; add a new bank account instead")
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
