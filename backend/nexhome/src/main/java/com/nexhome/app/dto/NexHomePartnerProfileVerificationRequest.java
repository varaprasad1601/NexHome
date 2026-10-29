package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Staff record the partner profile's account check (identity and contact). A null (or missing) value keeps it; true
 * records the acting staff member as who verified it, false clears that. Documents and bank accounts are verified one
 * by one (PUT /documents/verify, /bank-accounts/verify).
 */
public record NexHomePartnerProfileVerificationRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		Boolean accountVerified) {
}
