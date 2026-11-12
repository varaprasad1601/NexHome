package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * The partner payouts screen's figures (V62): {@code totalDue} (what NexHome owes all partners with a balance),
 * {@code partnersDue}, everything paid ({@code paidTotal}, {@code payoutCount}) and this month (IST).
 */
public record NexHomeManualPayoutStatsResponse(
		BigDecimal totalDue,
		long partnersDue,
		BigDecimal paidTotal,
		long payoutCount,
		BigDecimal paidThisMonth,
		long payoutsThisMonth,
		BigDecimal deductedTotal,
		long deductionCount) {
}
