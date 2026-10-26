package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff decision on a document: a VERIFICATION_STATUS code (IN_REVIEW, VERIFIED, REJECTED or EXPIRED). The reason is
 * required for REJECTED.
 */
public record NexHomePartnerDocumentVerifyRequest(
		@NotNull(message = "Partner document ID is required")
		Long partnerDocumentId,

		@NotBlank(message = "Verification status is required")
		String verificationStatus,

		@Size(max = 2000, message = "Rejection reason must be at most 2000 characters")
		String rejectionReason) {
}
