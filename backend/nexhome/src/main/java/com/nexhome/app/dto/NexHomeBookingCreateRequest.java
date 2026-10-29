package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Staff book a service for a customer (e.g. a phone booking). {@code minutes} is the expected time of an HOURLY
 * service without a package (default: one hour). {@code slotRequested: true} records that no partner was free when it
 * was booked, so staff fit a slot in and assign a partner (Assign Partner, outside weekly hours if need be).
 */
public record NexHomeBookingCreateRequest(
		@NotNull(message = "Customer profile ID is required")
		Long customerProfileId,

		@NotNull(message = "Service ID is required")
		Long serviceId,

		Long serviceVariantId,

		@NotNull(message = "Address ID is required")
		Long addressId,

		@NotBlank(message = "Booking type is required")
		String bookingType,

		Instant scheduledStartAt,

		@NotBlank(message = "Payment method is required")
		String paymentMethod,

		@Min(value = 15, message = "Minutes must be between 15 and 1440")
		@Max(value = 1440, message = "Minutes must be between 15 and 1440")
		Integer minutes,

		Boolean slotRequested) {
}
