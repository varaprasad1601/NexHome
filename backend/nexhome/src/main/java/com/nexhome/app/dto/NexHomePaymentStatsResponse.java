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
		List<MonthRevenue> revenueMonths,
		/** What the payment gateway has taken in all (charges + tax of the payments it reported, V68). */
		BigDecimal gatewayChargesAmount,
		/** Cash partners collected (COLLECTED cash collections): amount and count. */
		BigDecimal cashCollectedAmount,
		long cashCollectedCount,
		/** Bookings not paid yet (payment PENDING or FAILED, cash and online): their total and count. */
		BigDecimal pendingPaymentAmount,
		long pendingPaymentCount,
		/** The amount of the open orders ({@code ordersAwaitingPayment} is their count). */
		BigDecimal ordersAwaitingAmount) {

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
