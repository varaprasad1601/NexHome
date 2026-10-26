package com.nexhome.app.dto;

import java.math.BigDecimal;

/**
 * The price of a service (and variant) for the given quantity or time, before taxes and discounts.
 *
 * @param unitPrice the variant's price override, else the service's base price (per hour for HOURLY, per unit for
 *        PER_UNIT)
 * @param billableMinutes HOURLY only: the requested minutes raised to the minimum and rounded up to the increment
 * @param subtotal the unit price times the quantity or billable hours
 * @param total the subtotal, raised to the minimum charge
 * @param durationMinutes the expected duration: billable minutes for HOURLY, else the variant's, else the service's
 *        estimate
 */
public record NexHomeServiceQuoteResponse(
		Long serviceId,
		Long serviceVariantId,
		String pricingType,
		BigDecimal unitPrice,
		Integer quantity,
		Integer billableMinutes,
		BigDecimal subtotal,
		BigDecimal minimumCharge,
		BigDecimal total,
		Integer durationMinutes) {
}
