package com.nexhome.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Replaces the number, file or expiry date of a document that is not VERIFIED; the document goes back to PENDING
 * verification. A null (or missing) field keeps its current value and an empty document number clears it. The
 * partner and document type are fixed: they are only declared here so that a request containing them is rejected
 * with a clear message (add a new document instead).
 */
public record NexHomePartnerDocumentUpdateRequest(
		@NotNull(message = "Partner document ID is required")
		Long partnerDocumentId,

		@Null(message = "Partner profile cannot be updated")
		Long partnerProfileId,

		@Null(message = "Document type cannot be updated")
		String documentType,

		@Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 /-]{2,28}[A-Za-z0-9]$",
				message = "Document number must be 4 to 30 letters, digits, spaces, slashes or hyphens")
		String documentNumber,

		@Pattern(regexp = "(?s).*\\S.*", message = "File storage key cannot be blank")
		@Size(max = 1024, message = "File storage key must be at most 1024 characters")
		String fileStorageKey,

		@FutureOrPresent(message = "Expiry date cannot be in the past")
		LocalDate expiryDate,

		@Null(message = "Verification status cannot be updated here")
		String verificationStatus) {

	/** Never print the document number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerDocumentUpdateRequest[partnerDocumentId=" + partnerDocumentId + ", documentNumber="
				+ (documentNumber == null ? null : "****") + "]";
	}
}
