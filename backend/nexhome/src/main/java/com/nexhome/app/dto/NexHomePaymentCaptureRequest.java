package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff record that a payment order was paid (the MANUAL provider's capture). {@code providerPaymentId}: the
 * payment's reference, e.g. the UPI transaction id (optional).
 */
public record NexHomePaymentCaptureRequest(
		@NotNull(message = "Payment order ID is required")
		Long paymentOrderId,

		@Size(max = 150, message = "Payment reference must be at most 150 characters")
		String providerPaymentId) {
}
