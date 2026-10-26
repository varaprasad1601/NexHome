package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A partner bank account. Only the last four digits of the account number are ever returned. */
public record NexHomePartnerBankAccountResponse(
		Long partnerBankAccountId,
		Long partnerProfileId,
		String accountHolderName,
		String accountNumberLast4,
		String ifscCode,
		String bankName,
		String verificationStatus,
		boolean isPrimary,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
