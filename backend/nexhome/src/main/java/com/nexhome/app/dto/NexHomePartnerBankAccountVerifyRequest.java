package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Staff decision on a bank account: a VERIFICATION_STATUS code (IN_REVIEW, VERIFIED, REJECTED or EXPIRED). */
public record NexHomePartnerBankAccountVerifyRequest(
		@NotNull(message = "Partner bank account ID is required")
		Long partnerBankAccountId,

		@NotBlank(message = "Verification status is required")
		String verificationStatus) {
}
