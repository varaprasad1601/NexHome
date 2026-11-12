package com.nexhome.app.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner document. The document number is masked ({@code XXXXXX1234}); {@code fullDocumentNumber} is the number
 * itself, filled only for the partner who owns the document (their own documents list, so the app's edit form shows
 * it), null for staff and everywhere else. The file (when one was uploaded) opens through
 * {@code GET /api/files/download-url/{fileId}}.
 */
public record NexHomePartnerDocumentResponse(
		Long partnerDocumentId,
		Long partnerProfileId,
		String documentType,
		String documentNumber,
		Long fileId,
		String fileName,
		String fileContentType,
		Long fileSize,
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

		String documentName,

		String fullDocumentNumber) {
}
