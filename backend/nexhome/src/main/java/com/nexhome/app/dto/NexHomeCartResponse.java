package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The signed-in customer's active cart (empty, with a null id, before anything is added).
 */
public record NexHomeCartResponse(
		Long cartId,
		Long customerProfileId,
		String status,
		List<NexHomeCartItemResponse> items,
		BigDecimal estimatedTotal) {
}
