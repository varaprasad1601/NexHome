package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** Asks the payment gateway whether this order was paid. */
public record NexHomePaymentOrderVerifyRequest(
		@NotNull(message = "Payment order ID is required")
		Long paymentOrderId) {
}
