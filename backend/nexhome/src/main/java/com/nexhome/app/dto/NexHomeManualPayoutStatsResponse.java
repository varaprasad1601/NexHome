package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * The partner payouts screen's figures (V62): {@code totalDue} (what NexHome owes all partners with a balance),
 * {@code partnersDue}, everything paid ({@code paidTotal}, {@code payoutCount}) and this month (IST), deductions,
 * and NexHome's commission on every completed job ({@code commissionTotal}: online payouts not cancelled plus
 * collected cash jobs, {@code commissionJobs} those jobs).
 */
public record NexHomeManualPayoutStatsResponse(
		BigDecimal totalDue,
		long partnersDue,
		BigDecimal paidTotal,
		long payoutCount,
		BigDecimal paidThisMonth,
		long payoutsThisMonth,
		BigDecimal deductedTotal,
		long deductionCount,
		BigDecimal commissionTotal,
		long commissionJobs) {
}
