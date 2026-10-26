package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner profile. The Aadhaar number is always masked ({@code XXXXXXXX9012}). The emergency contact is only shown
 * to the partner, or to staff once the partner has given permission; otherwise its fields are null.
 */
public record NexHomePartnerProfileResponse(
		Long partnerProfileId,
		String publicId,
		Long userId,
		String firstName,
		String middleName,
		String lastName,
		String aadhaarNumber,
		String alternateContactNumber,
		Boolean isAdult,
		String preferredContactLanguage,
		String addressLine1,
		String addressLine2,
		String landmark,
		String locality,
		String city,
		String state,
		String postalCode,
		String countryCode,
		BigDecimal latitude,
		BigDecimal longitude,
		LocalDate registrationDate,
		Long registeredBy,
		String skillCategory,
		Short yearsExperience,
		List<String> additionalSkills,
		String preferredArrangement,
		String paymentBasis,
		BigDecimal expectedEarningsInr,
		BigDecimal visitChargeInr,
		BigDecimal minimumJobPaymentInr,
		String previousWorkDetails,
		List<String> serviceAreas,
		BigDecimal maxTravelKm,
		Map<String, List<String>> weeklyAvailability,
		String weekendAvailability,
		String emergencyContactName,
		String emergencyContactRelationship,
		String emergencyContactNumber,
		boolean emergencyContactPermission,
		String onboardingStatus,
		String verificationStatus,
		String availabilityStatus,
		Long approvedBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant approvedAt,
		String rejectionReason,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
