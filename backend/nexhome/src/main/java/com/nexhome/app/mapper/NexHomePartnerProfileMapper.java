package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.collapseWhitespace;
import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeFieldEncryptor;
import com.nexhome.app.dto.NexHomePartnerProfileFields;
import com.nexhome.app.dto.NexHomePartnerProfileResponse;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerAvailabilityStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerOnboardingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.VerificationStatus;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeUser;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class NexHomePartnerProfileMapper {

	private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, List<String>>> AVAILABILITY_TYPE = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;
	private final NexHomeFieldEncryptor encryptor;

	/**
	 * A new profile is a DRAFT, PENDING verification and OFFLINE, registered today by the acting user unless staff
	 * gave another date. The submitted fields are applied by {@link #applyPartnerProfileFields}.
	 */
	public NexHomePartnerProfile toPartnerProfileEntity(NexHomeUser user, Long actingUserId) {
		NexHomePartnerProfile profile = new NexHomePartnerProfile();
		profile.setUser(user);
		profile.setRegistrationDate(LocalDate.now());
		profile.setRegisteredBy(actingUserId);
		profile.setOnboardingStatus(PartnerOnboardingStatus.DRAFT);
		profile.setVerificationStatus(VerificationStatus.PENDING);
		profile.setAvailabilityStatus(PartnerAvailabilityStatus.OFFLINE);
		profile.setCreatedBy(actingUserId);
		profile.setUpdatedBy(actingUserId);
		return profile;
	}

	/**
	 * Applies the scalar fields of a create or update: a null field keeps its current value and an empty optional
	 * text field is cleared. Names are whitespace-collapsed, the Aadhaar number is encrypted and coordinates are
	 * rounded to 7 decimals (set only as a pair). Lookup codes are validated by the service beforehand; the main
	 * service category and the JSON fields (skills, service areas, weekly availability) are set by the service.
	 */
	public void applyPartnerProfileFields(NexHomePartnerProfile profile, NexHomePartnerProfileFields fields,
			Long actingUserId) {
		if (fields.firstName() != null) {
			profile.setFirstName(collapseWhitespace(fields.firstName()));
		}
		if (fields.middleName() != null) {
			profile.setMiddleName(collapseWhitespace(fields.middleName()));
		}
		if (fields.lastName() != null) {
			profile.setLastName(collapseWhitespace(fields.lastName()));
		}
		if (fields.aadhaarNumber() != null) {
			profile.setAadhaarNumberEncrypted(encryptor.encrypt(trimToNull(fields.aadhaarNumber())));
		}
		if (fields.alternateContactNumber() != null) {
			profile.setAlternateContactNumber(trimToNull(fields.alternateContactNumber()));
		}
		if (fields.isAdult() != null) {
			profile.setAdult(fields.isAdult());
		}
		if (fields.preferredContactLanguage() != null) {
			profile.setPreferredContactLanguage(trimToNull(fields.preferredContactLanguage()));
		}
		if (fields.addressLine1() != null) {
			profile.setAddressLine1(trimToNull(fields.addressLine1()));
		}
		if (fields.addressLine2() != null) {
			profile.setAddressLine2(trimToNull(fields.addressLine2()));
		}
		if (fields.landmark() != null) {
			profile.setLandmark(trimToNull(fields.landmark()));
		}
		if (fields.locality() != null) {
			profile.setLocality(trimToNull(fields.locality()));
		}
		if (fields.city() != null) {
			profile.setCity(trimToNull(fields.city()));
		}
		if (fields.state() != null) {
			profile.setState(trimToNull(fields.state()));
		}
		if (fields.postalCode() != null) {
			profile.setPostalCode(trimToNull(fields.postalCode()));
		}
		if (fields.latitude() != null && fields.longitude() != null) {
			profile.setLatitude(NexHomeAddressMapper.coordinate(fields.latitude()));
			profile.setLongitude(NexHomeAddressMapper.coordinate(fields.longitude()));
		}
		if (fields.registrationDate() != null) {
			profile.setRegistrationDate(fields.registrationDate());
		}
		if (fields.skillCategory() != null) {
			profile.setSkillCategory(trimToNull(fields.skillCategory()));
		}
		if (fields.yearsExperience() != null) {
			profile.setYearsExperience(fields.yearsExperience().shortValue());
		}
		if (fields.preferredArrangement() != null) {
			profile.setPreferredArrangement(trimToNull(fields.preferredArrangement()));
		}
		if (fields.paymentBasis() != null) {
			profile.setPaymentBasis(trimToNull(fields.paymentBasis()));
		}
		if (fields.expectedEarningsInr() != null) {
			profile.setExpectedEarningsInr(fields.expectedEarningsInr());
		}
		if (fields.visitChargeInr() != null) {
			profile.setVisitChargeInr(fields.visitChargeInr());
		}
		if (fields.minimumJobPaymentInr() != null) {
			profile.setMinimumJobPaymentInr(fields.minimumJobPaymentInr());
		}
		if (fields.previousWorkDetails() != null) {
			profile.setPreviousWorkDetails(trimToNull(fields.previousWorkDetails()));
		}
		if (fields.maxTravelKm() != null) {
			profile.setMaxTravelKm(fields.maxTravelKm());
		}
		if (fields.weekendAvailability() != null) {
			profile.setWeekendAvailability(trimToNull(fields.weekendAvailability()));
		}
		if (fields.emergencyContactName() != null) {
			profile.setEmergencyContactName(collapseWhitespace(fields.emergencyContactName()));
		}
		if (fields.emergencyContactRelationship() != null) {
			profile.setEmergencyContactRelationship(trimToNull(fields.emergencyContactRelationship()));
		}
		if (fields.emergencyContactNumber() != null) {
			profile.setEmergencyContactNumber(trimToNull(fields.emergencyContactNumber()));
		}
		if (fields.emergencyContactPermission() != null) {
			profile.setEmergencyContactPermission(fields.emergencyContactPermission());
		}
		profile.setUpdatedBy(actingUserId);
	}

	/**
	 * @param showEmergencyContact false hides the emergency contact (for viewers other than the partner while the
	 *        partner has not given permission)
	 */
	public NexHomePartnerProfileResponse toPartnerProfileDto(NexHomePartnerProfile profile,
			boolean showEmergencyContact) {
		return new NexHomePartnerProfileResponse(
				profile.getId(),
				profile.getPublicId(),
				profile.getUser().getId(),
				profile.getFirstName(),
				profile.getMiddleName(),
				profile.getLastName(),
				NexHomeFieldEncryptor.mask(encryptor.decrypt(profile.getAadhaarNumberEncrypted())),
				profile.getAlternateContactNumber(),
				profile.getAdult(),
				profile.getPreferredContactLanguage(),
				profile.getAddressLine1(),
				profile.getAddressLine2(),
				profile.getLandmark(),
				profile.getLocality(),
				profile.getCity(),
				profile.getState(),
				profile.getPostalCode(),
				profile.getCountryCode(),
				profile.getLatitude(),
				profile.getLongitude(),
				profile.getRegistrationDate(),
				profile.getRegisteredBy(),
				profile.getSkillCategory(),
				profile.getYearsExperience(),
				profile.getMainServiceCategory() == null ? null : profile.getMainServiceCategory().getId(),
				profile.getAdditionalSkills() == null ? null : readList(profile.getAdditionalSkills()),
				profile.getPreferredArrangement(),
				profile.getPaymentBasis(),
				profile.getExpectedEarningsInr(),
				profile.getVisitChargeInr(),
				profile.getMinimumJobPaymentInr(),
				profile.getPreviousWorkDetails(),
				readList(profile.getServiceAreas()),
				profile.getMaxTravelKm(),
				readAvailability(profile.getWeeklyAvailability()),
				profile.getWeekendAvailability(),
				showEmergencyContact ? profile.getEmergencyContactName() : null,
				showEmergencyContact ? profile.getEmergencyContactRelationship() : null,
				showEmergencyContact ? profile.getEmergencyContactNumber() : null,
				profile.isEmergencyContactPermission(),
				profile.getOnboardingStatus(),
				profile.getVerificationStatus(),
				profile.getAvailabilityStatus(),
				profile.getApprovedBy(),
				profile.getApprovedAt(),
				profile.getRejectionReason(),
				profile.getCreationDate());
	}

	/** Serializes a normalized list or availability map for a JSONB column. */
	public String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	/** A JSON array column (skills, service areas) as a list. */
	public List<String> readList(String json) {
		return jsonMapper.readValue(json, LIST_TYPE);
	}

	/** The weekly availability column as day to slot codes. */
	public Map<String, List<String>> readAvailability(String json) {
		return jsonMapper.readValue(json, AVAILABILITY_TYPE);
	}
}
