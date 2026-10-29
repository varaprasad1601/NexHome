package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The editable fields of a partner profile, shared by {@link NexHomePartnerProfileCreateRequest} and
 * {@link NexHomePartnerProfileUpdateRequest} so that both are applied the same way. A null field is left unchanged.
 */
public interface NexHomePartnerProfileFields {

	String firstName();

	String middleName();

	String lastName();

	String aadhaarNumber();

	String alternateContactNumber();

	Boolean isAdult();

	String preferredContactLanguage();

	String addressLine1();

	String addressLine2();

	String landmark();

	String locality();

	String city();

	String state();

	String postalCode();

	BigDecimal latitude();

	BigDecimal longitude();

	LocalDate registrationDate();

	String skillCategory();

	Integer yearsExperience();

	Long mainServiceCategoryId();

	List<String> additionalSkills();

	String preferredArrangement();

	String paymentBasis();

	BigDecimal expectedEarningsInr();

	BigDecimal visitChargeInr();

	BigDecimal minimumJobPaymentInr();

	String previousWorkDetails();

	List<String> serviceAreas();

	BigDecimal maxTravelKm();

	Map<String, List<String>> weeklyAvailability();

	String weekendAvailability();

	String emergencyContactName();

	String emergencyContactRelationship();

	String emergencyContactNumber();

}
