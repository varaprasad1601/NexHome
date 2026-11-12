package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * What NexHome owes one partner (V62): {@code totalEarned} (every completed job, cash and online, less commission),
 * {@code paidTotal} (recorded payouts), {@code deductedTotal} (recorded deductions, V63), {@code balance} = earned -
 * paid - deducted (negative: paid too much).
 */
public record NexHomePartnerDueResponse(
		Long partnerProfileId,
		String publicId,
		String partnerName,
		String mobileNumber,
		long jobs,
		BigDecimal totalEarned,
		BigDecimal paidTotal,
		BigDecimal deductedTotal,
		BigDecimal balance) {
}
