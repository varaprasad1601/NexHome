package com.nexhome.app.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds an identity or certification document to a partner profile, PENDING verification. Its scan or photo is
 * uploaded first ({@code POST /api/files/upload}, purpose PARTNER_DOCUMENT: PDF, JPEG or PNG up to 10 MB) and given
 * here as {@code fileId} (optional). The document number is stored encrypted and only ever returned masked.
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

		// The caller's upload (purpose PARTNER_DOCUMENT); optional
		Long fileId,

		@FutureOrPresent(message = "Expiry date cannot be in the past")
		LocalDate expiryDate,

		// GOVERNMENT_ID_TYPE code: required for a GOVERNMENT_ID document
		String idType,

		// Required for a CERTIFICATION (skill certificate) document, e.g. "ITI Electrician"
		@Size(max = 150, message = "Document name must be at most 150 characters")
		String documentName) {

	/** Never print the document number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerDocumentCreateRequest[partnerProfileId=" + partnerProfileId + ", documentType="
				+ documentType + ", documentNumber=" + (documentNumber == null ? null : "****") + "]";
	}
}
