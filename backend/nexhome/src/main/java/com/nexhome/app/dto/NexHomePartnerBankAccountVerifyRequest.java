package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff decision on a bank account: a VERIFICATION_STATUS code (IN_REVIEW, VERIFIED, REJECTED or EXPIRED), with the
 * reason when REJECTED.
 */
public record NexHomePartnerBankAccountVerifyRequest(
		@NotNull(message = "Partner bank account ID is required")
		Long partnerBankAccountId,

		@NotBlank(message = "Verification status is required")
		String verificationStatus,

		@Size(max = 2000, message = "Rejection reason must be at most 2000 characters")
		String rejectionReason) {
}
