package com.nexhome.app.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A partner document. The document number is masked ({@code XXXXXX1234}). */
public record NexHomePartnerDocumentResponse(
		Long partnerDocumentId,
		Long partnerProfileId,
		String documentType,
		String documentNumber,
		String fileStorageKey,
		String verificationStatus,
		Long verifiedBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant verifiedAt,
		LocalDate expiryDate,
		String rejectionReason,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,

		String idType,

		String documentName) {
}
