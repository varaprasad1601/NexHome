package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner bank account. Only the last four digits of the account number are ever returned. {@code updatedBy} /
 * {@code updatedDate} are the last change; after a verification decision, who made it and when.
 */
public record NexHomePartnerBankAccountResponse(
		Long partnerBankAccountId,
		Long partnerProfileId,
		String accountHolderName,
		String accountNumberLast4,
		String ifscCode,
		String bankName,
		String branchName,
		String verificationStatus,
		String rejectionReason,
		boolean isPrimary,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,
		Long updatedBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant updatedDate) {
}
