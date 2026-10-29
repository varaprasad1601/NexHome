package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner's earnings in a period (TODAY, WEEK: since Monday, MONTH: since the 1st, ALL; IST, by when the job was
 * completed): the jobs, their total, the platform's commission and the partner's earnings, and a page of the records.
 *
 * @param from the start of the period; null for ALL
 */
public record NexHomePartnerEarningsResponse(
		String period,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant from,
		long jobs,
		BigDecimal totalAmount,
		BigDecimal commission,
		BigDecimal earnings,
		NexHomePageResponse<Record> records) {

	/**
	 * One completed job. A paid online job's figures are its payout's (the total is the amount kept less the gateway
	 * fee and refunds; the earnings include any adjustment); a cash job's, or an online job not yet paid, are the total
	 * less the gateway fee and the service's commission. {@code settlement}: PAYOUT (then the payout's status) or
	 * CASH (the cash collection's), null while none exists.
	 */
	public record Record(
			Long bookingId,
			String bookingPublicId,
			String serviceName,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
					timezone = "Asia/Kolkata")
			Instant completedAt,
			String paymentMethod,
			BigDecimal totalAmount,
			BigDecimal commission,
			BigDecimal partnerEarnings,
			String settlement,
			String settlementStatus) {
	}
}
