package com.nexhome.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds an identity or certification document to a partner profile, PENDING verification. The file is uploaded to
 * protected storage first; {@code fileStorageKey} is its key there. The document number is stored encrypted and only
 * ever returned masked.
 */
public record NexHomePartnerDocumentCreateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		// DOCUMENT_TYPE code: GOVERNMENT_ID, ADDRESS_PROOF, POLICE_CHECK, CERTIFICATION, OTHER
		@NotBlank(message = "Document type is required")
		String documentType,

		@Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9 /-]{2,28}[A-Za-z0-9]$",
				message = "Document number must be 4 to 30 letters, digits, spaces, slashes or hyphens")
		String documentNumber,

		@NotBlank(message = "File storage key is required")
		@Size(max = 1024, message = "File storage key must be at most 1024 characters")
		String fileStorageKey,

		@FutureOrPresent(message = "Expiry date cannot be in the past")
		LocalDate expiryDate) {

	/** Never print the document number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerDocumentCreateRequest[partnerProfileId=" + partnerProfileId + ", documentType="
				+ documentType + ", documentNumber=" + (documentNumber == null ? null : "****") + "]";
	}
}
