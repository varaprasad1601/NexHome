package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner's wallet (V60): what is not settled yet. Online jobs NexHome received (the partner is owed the amount
 * less commission plus adjustments) and cash jobs the partner collected (the partner kept the cash and owes its
 * commission). {@code balance} = {@code onlineEarnings} - {@code cashCommission}: what NexHome pays at the next
 * payout, or, when negative, what the partner owes. {@code since} is the last payout's time (null before any).
 */
public record NexHomePartnerWalletResponse(Long partnerProfileId,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata") Instant since,
		int cashJobs, BigDecimal cashAmount, BigDecimal cashCommission, int onlineJobs, BigDecimal onlineAmount,
		BigDecimal onlineCommission, BigDecimal adjustmentsAmount, BigDecimal onlineEarnings, BigDecimal balance,
		int onHoldJobs, BigDecimal onHoldAmount, BigDecimal paidTotal, BigDecimal cashKeptTotal,
		NexHomePartnerSettlementResponse lastSettlement, List<Item> items) {

	/**
	 * One job in a wallet or a payout: {@code amount} (cash collected, or the online amount less the transaction
	 * fee), {@code commission}, {@code adjustments} (online only) and {@code effect} on the payout (online: + the
	 * partner's share; cash: - the commission).
	 */
	public record Item(Long bookingId, String bookingPublicId, String serviceName,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
					timezone = "Asia/Kolkata") Instant completedAt,
			String paymentMethod, BigDecimal amount, BigDecimal commission, BigDecimal adjustments,
			BigDecimal effect) {
	}
}
