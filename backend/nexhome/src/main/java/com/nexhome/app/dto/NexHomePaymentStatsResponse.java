package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Money in: captured, refunded, orders waiting, cash still to collect, and revenue (payments captured plus cash
 * collected, less refunds) in all, this month and per month (IST).
 *
 * @param revenueMonths the last 10 months, oldest first, ending with this month
 */
public record NexHomePaymentStatsResponse(
		BigDecimal capturedAmount,
		long capturedCount,
		BigDecimal refundedAmount,
		long refundCount,
		long ordersAwaitingPayment,
		BigDecimal cashDueAmount,
		long cashDueCount,
		BigDecimal revenueAmount,
		BigDecimal revenueThisMonth,
		List<MonthRevenue> revenueMonths) {

	/**
	 * @param month {@code YYYY-MM}
	 * @param added revenue of that month
	 * @param total revenue up to the end of that month (for this month: now)
	 */
	public record MonthRevenue(
			String month,
			BigDecimal added,
			BigDecimal total) {
	}
}
