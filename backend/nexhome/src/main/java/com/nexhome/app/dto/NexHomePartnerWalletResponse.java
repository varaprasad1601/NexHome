package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * What NexHome owes a partner (V62), since joining. Partners hand the cash of cash jobs to NexHome, so every completed
 * job counts: cash jobs (cash COLLECTED) and online jobs (their payouts, not cancelled), each less its commission
 * (online also plus adjustments). {@code totalAmount} = cash + online, {@code totalCommission}, {@code totalEarned}
 * = amount - commission + adjustments; {@code paidTotal} the payouts staff recorded ({@code payoutCount}, the latest
 * {@code lastPayout}), {@code deductedTotal} / {@code deductionCount} what NexHome kept (V63); {@code balance} =
 * earned - paid - deducted (what NexHome still has to pay; negative: paid too much).
 * {@code items}: the latest {@value #MAX_ITEMS} jobs ({@code jobCount} all of them).
 */
public record NexHomePartnerWalletResponse(Long partnerProfileId,
		int cashJobs, BigDecimal cashAmount, BigDecimal cashCommission,
		int onlineJobs, BigDecimal onlineAmount, BigDecimal onlineCommission, BigDecimal adjustmentsAmount,
		BigDecimal totalAmount, BigDecimal totalCommission, BigDecimal totalEarned,
		BigDecimal paidTotal, long payoutCount, BigDecimal deductedTotal, long deductionCount, BigDecimal balance,
		NexHomeManualPayoutResponse lastPayout, int jobCount, List<Item> items) {

	public static final int MAX_ITEMS = 50;

	/**
	 * One job: {@code amount} (cash collected, or the online amount less the transaction fee), {@code commission},
	 * {@code adjustments} (online only) and {@code effect}, the partner's share (amount - commission + adjustments).
	 */
	public record Item(Long bookingId, String bookingPublicId, String serviceName,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
					timezone = "Asia/Kolkata") Instant completedAt,
			String paymentMethod, BigDecimal amount, BigDecimal commission, BigDecimal adjustments,
			BigDecimal effect) {
	}
}
