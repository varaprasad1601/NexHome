package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds a payout bank account to a partner profile, PENDING verification. The first account becomes primary;
 * {@code isPrimary: true} makes a later one primary instead. The account number is stored encrypted; only its last
 * four digits are ever returned.
 */
public record NexHomePartnerBankAccountCreateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@NotBlank(message = "Account holder name is required")
		@Size(max = 150, message = "Account holder name must be at most 150 characters")
		String accountHolderName,

		@NotBlank(message = "Account number is required")
		@Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be 9 to 18 digits")
		String accountNumber,

		// Stored upper-cased
		@NotBlank(message = "IFSC code is required")
		@Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$",
				message = "IFSC code must be 4 letters, a zero and 6 letters or digits")
		String ifscCode,

		@Size(max = 150, message = "Bank name must be at most 150 characters")
		String bankName,

		@Size(max = 150, message = "Branch must be at most 150 characters")
		String branchName,

		Boolean isPrimary) {

	/** Never print the account number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerBankAccountCreateRequest[partnerProfileId=" + partnerProfileId + ", ifscCode="
				+ ifscCode + ", accountNumber=****]";
	}
}
