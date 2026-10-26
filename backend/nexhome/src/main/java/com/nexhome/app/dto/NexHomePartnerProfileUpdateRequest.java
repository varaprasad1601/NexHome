package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a partner profile. A null (or missing) field keeps its current value. Required fields cannot be blanked;
 * an empty optional text field (middle name, Aadhaar number, alternate contact number, address line 2, landmark,
 * previous work details, emergency contact fields) clears it, as does an empty list or availability map. Numbers
 * cannot be cleared once set.
 * <p>
 * Partners can change their profile only while it is a DRAFT or PROFILE_INCOMPLETE; staff with
 * PARTNER_ONBOARDING_MANAGE write can change it at any time, and only they can change the registration date. The
 * owner, public id and statuses are only declared here so that a request containing them is rejected with a clear
 * message: statuses change through submit, review and availability.
 */
public record NexHomePartnerProfileUpdateRequest(
		@NotNull(message = "Partner profile ID is required")
		Long partnerProfileId,

		@Null(message = "User cannot be updated")
		Long userId,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		@Null(message = "Onboarding status cannot be updated here")
		String onboardingStatus,

		@Null(message = "Verification status cannot be updated here")
		String verificationStatus,

		@Null(message = "Availability status cannot be updated here")
		String availabilityStatus,

		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "First name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 80, message = "First name must be at most 80 characters")
		String firstName,

		@Pattern(regexp = "^$|^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "Middle name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 80, message = "Middle name must be at most 80 characters")
		String middleName,

		@Pattern(regexp = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}\\s.'-]*$",
				message = "Last name must contain only letters, spaces, dots, apostrophes and hyphens")
		@Size(max = 80, message = "Last name must be at most 80 characters")
		String lastName,

		@Pattern(regexp = "^$|^[2-9][0-9]{11}$",
				message = "Aadhaar number must be 12 digits and cannot start with 0 or 1")
		String aadhaarNumber,

		@Pattern(regexp = "^$|^[6-9][0-9]{9}$",
				message = "Alternate contact number must be a 10-digit Indian mobile number")
		String alternateContactNumber,

		Boolean isAdult,

		String preferredContactLanguage,

		@Pattern(regexp = "(?s).*\\S.*", message = "Address line 1 cannot be blank")
		@Size(max = 300, message = "Address line 1 must be at most 300 characters")
		String addressLine1,

		@Size(max = 255, message = "Address line 2 must be at most 255 characters")
		String addressLine2,

		@Size(max = 255, message = "Landmark must be at most 255 characters")
		String landmark,

		@Pattern(regexp = "(?s).*\\S.*", message = "Locality cannot be blank")
		@Size(max = 100, message = "Locality must be at most 100 characters")
		String locality,

		@Pattern(regexp = "(?s).*\\S.*", message = "City cannot be blank")
		@Size(max = 100, message = "City must be at most 100 characters")
		String city,

		@Pattern(regexp = "(?s).*\\S.*", message = "State cannot be blank")
		@Size(max = 100, message = "State must be at most 100 characters")
		String state,

		@Pattern(regexp = "^[1-9][0-9]{5}$", message = "Postal code must be a 6-digit PIN code")
		String postalCode,

		@DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
		@DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
		BigDecimal latitude,

		@DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
		@DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
		BigDecimal longitude,

		@PastOrPresent(message = "Registration date cannot be in the future")
		LocalDate registrationDate,

		String skillCategory,

		@Min(value = 0, message = "Years of experience must be between 0 and 60")
		@Max(value = 60, message = "Years of experience must be between 0 and 60")
		Integer yearsExperience,

		// An active service category: the partner's main skill. Required to submit
		Long mainServiceCategoryId,

		@Size(max = 30, message = "Additional skills must have at most 30 items")
		List<@NotBlank(message = "Additional skills cannot contain blank items")
				@Size(max = 80, message = "Each additional skill must be at most 80 characters") String> additionalSkills,

		String preferredArrangement,

		String paymentBasis,

		@DecimalMin(value = "0.01", message = "Expected earnings must be more than 0")
		@Digits(integer = 8, fraction = 2, message = "Expected earnings must have at most 8 digits and 2 decimals")
		BigDecimal expectedEarningsInr,

		@DecimalMin(value = "0", message = "Visit charge cannot be negative")
		@Digits(integer = 8, fraction = 2, message = "Visit charge must have at most 8 digits and 2 decimals")
		BigDecimal visitChargeInr,

		@DecimalMin(value = "0", message = "Minimum job payment cannot be negative")
		@Digits(integer = 8, fraction = 2, message = "Minimum job payment must have at most 8 digits and 2 decimals")
		BigDecimal minimumJobPaymentInr,

		@Size(max = 2000, message = "Previous work details must be at most 2000 characters")
		String previousWorkDetails,

		@Size(max = 50, message = "Service areas must have at most 50 items")
		List<@NotBlank(message = "Service areas cannot contain blank items")
				@Size(max = 100, message = "Each service area must be at most 100 characters") String> serviceAreas,

		@DecimalMin(value = "0", message = "Maximum travel distance must be between 0 and 999.9 km")
		@DecimalMax(value = "999.9", message = "Maximum travel distance must be between 0 and 999.9 km")
		@Digits(integer = 3, fraction = 1, message = "Maximum travel distance must have at most 1 decimal")
		BigDecimal maxTravelKm,

		Map<String, List<String>> weeklyAvailability,

		String weekendAvailability,

		@Size(max = 120, message = "Emergency contact name must be at most 120 characters")
		String emergencyContactName,

		String emergencyContactRelationship,

		@Pattern(regexp = "^$|^[6-9][0-9]{9}$",
				message = "Emergency contact number must be a 10-digit Indian mobile number")
		String emergencyContactNumber,

		Boolean emergencyContactPermission) implements NexHomePartnerProfileFields {

	/** Never print the Aadhaar number, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomePartnerProfileUpdateRequest[partnerProfileId=" + partnerProfileId + ", aadhaarNumber="
				+ (aadhaarNumber == null ? null : "****") + "]";
	}
}
