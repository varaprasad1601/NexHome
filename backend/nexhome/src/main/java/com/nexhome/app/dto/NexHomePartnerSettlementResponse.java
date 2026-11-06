package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A wallet payout (V60): the cash and online jobs it settled with their commission, {@code onlineEarnings}
 * (online less commission plus adjustments) and {@code netAmount} paid (= onlineEarnings - cashCommission).
 * {@code items} only on the single payout.
 */
public record NexHomePartnerSettlementResponse(Long settlementId, String publicId, Long partnerProfileId,
		String partnerPublicId, int cashJobs, BigDecimal cashAmount, BigDecimal cashCommission, int onlineJobs,
		BigDecimal onlineAmount, BigDecimal onlineCommission, BigDecimal adjustmentsAmount,
		BigDecimal onlineEarnings, BigDecimal netAmount, String settlementStatus, String providerTransferId,
		String bankName, String bankAccountLast4,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata") Instant paidAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata") Instant creationDate,
		List<NexHomePartnerWalletResponse.Item> items) {
}
