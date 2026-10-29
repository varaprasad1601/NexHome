package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * The price of a service (and variant) for the given time, before taxes.
 *
 * @param regularPrice the variant's price, else the service's base price, before any discount
 * @param unitPrice what is charged: the discount price when there is one, else the regular price (per hour for HOURLY
 *        without a variant)
 * @param billableMinutes HOURLY only: the minutes billed (a package's hours plus any extra time, or the requested
 *        minutes raised to the minimum and rounded up to the increment)
 * @param subtotal the price for the job: the unit price once, or the hourly price for the billable time
 * @param total the subtotal, raised to the minimum charge
 * @param durationMinutes the expected duration: billable minutes for HOURLY, else the variant's, else the service's
 *        estimate
 */
public record NexHomeServiceQuoteResponse(
		Long serviceId,

		Long serviceVariantId,

		String pricingType,

		BigDecimal regularPrice,

		BigDecimal unitPrice,

		Integer billableMinutes,

		BigDecimal subtotal,

		BigDecimal minimumCharge,

		BigDecimal total,

		Integer durationMinutes) {
}
