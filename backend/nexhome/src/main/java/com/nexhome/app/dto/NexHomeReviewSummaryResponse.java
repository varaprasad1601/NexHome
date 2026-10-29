package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One partner's or customer's reviews at a glance: the average partner rating (one decimal place, null without
 * reviews), how many, and how many at each rating, 5 down to 1 (every rating present). A partner's count only
 * PUBLISHED reviews; a customer's every review they wrote.
 */
public record NexHomeReviewSummaryResponse(
		BigDecimal averageRating,
		long total,
		Map<Integer, Long> ratingCounts) {
}
