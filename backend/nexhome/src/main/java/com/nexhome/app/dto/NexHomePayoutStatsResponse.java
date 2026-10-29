package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payouts by status: how many and their net total.
 */
public record NexHomePayoutStatsResponse(
		Map<String, Long> countByStatus,
		Map<String, BigDecimal> netByStatus) {
}
