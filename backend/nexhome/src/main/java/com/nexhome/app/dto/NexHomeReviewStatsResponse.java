package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Review figures for the console and the dashboard. Averages are of PUBLISHED reviews (null without any), to one
 * decimal place.
 *
 * @param ratingCounts PUBLISHED reviews by partner rating, 1 to 5 (every rating present)
 * @param publishedThisMonth PUBLISHED reviews made this calendar month (IST)
 * @param months the last 10 months, oldest first, ending with this month
 */
public record NexHomeReviewStatsResponse(
		long total,
		long published,
		long hidden,
		long underReview,
		BigDecimal averagePartnerRating,
		BigDecimal averageServiceRating,
		Map<Integer, Long> ratingCounts,
		long publishedThisMonth,
		List<MonthRating> months) {

	/**
	 * @param month {@code YYYY-MM}
	 * @param reviews PUBLISHED reviews made in that month
	 * @param averageRating the average partner rating of every PUBLISHED review up to the end of that month (null
	 *        before the first)
	 */
	public record MonthRating(
			String month,
			long reviews,
			BigDecimal averageRating) {
	}
}
