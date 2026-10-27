package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.collapseWhitespace;
import static com.nexhome.app.mapper.NexHomeText.trimToNull;
import static com.nexhome.app.mapper.NexHomeText.upperCase;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeAdminProfileCreateRequest;
import com.nexhome.app.dto.NexHomeAdminProfileResponse;
import com.nexhome.app.dto.NexHomeAdminProfileUpdateRequest;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeAdminProfileMapper {

	/** Codes are upper-cased and the country defaults to IN; the service checks the postal code for the country. */
	public NexHomeAdminProfile toAdminProfileEntity(NexHomeAdminProfileCreateRequest request, NexHomeUser user,
			Long actingUserId) {
		NexHomeAdminProfile profile = new NexHomeAdminProfile();
		profile.setUser(user);
		profile.setFirstName(collapseWhitespace(request.firstName()));
		profile.setLastName(collapseWhitespace(request.lastName()));
		profile.setEmployeeCode(upperCase(request.employeeCode()));
		profile.setDepartment(trimToNull(request.department()));
		profile.setJobTitle(trimToNull(request.jobTitle()));
		profile.setAddressLine1(trimToNull(request.addressLine1()));
		profile.setAddressLine2(trimToNull(request.addressLine2()));
		profile.setLandmark(trimToNull(request.landmark()));
		profile.setCity(trimToNull(request.city()));
		profile.setState(trimToNull(request.state()));
		profile.setPostalCode(upperCase(request.postalCode()));
		if (request.countryCode() != null) {
			profile.setCountryCode(upperCase(request.countryCode()));
		}
		profile.setCreatedBy(actingUserId);
		profile.setUpdatedBy(actingUserId);
		return profile;
	}

	/** The empty staff profile created with a staff account; its owner fills in the names and address later. */
	public NexHomeAdminProfile toAdminProfileEntity(NexHomeUser user, Long actingUserId) {
		NexHomeAdminProfile profile = new NexHomeAdminProfile();
		profile.setUser(user);
		profile.setCreatedBy(actingUserId);
		profile.setUpdatedBy(actingUserId);
		return profile;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty optional field is cleared. The owner is
	 * never updated.
	 */
	public void updateAdminProfileEntity(NexHomeAdminProfile profile, NexHomeAdminProfileUpdateRequest request,
			Long actingUserId) {
		if (request.firstName() != null) {
			profile.setFirstName(collapseWhitespace(request.firstName()));
		}
		if (request.lastName() != null) {
			profile.setLastName(collapseWhitespace(request.lastName()));
		}
		if (request.employeeCode() != null) {
			profile.setEmployeeCode(upperCase(request.employeeCode()));
		}
		if (request.department() != null) {
			profile.setDepartment(trimToNull(request.department()));
		}
		if (request.jobTitle() != null) {
			profile.setJobTitle(trimToNull(request.jobTitle()));
		}
		if (request.addressLine1() != null) {
			profile.setAddressLine1(trimToNull(request.addressLine1()));
		}
		if (request.addressLine2() != null) {
			profile.setAddressLine2(trimToNull(request.addressLine2()));
		}
		if (request.landmark() != null) {
			profile.setLandmark(trimToNull(request.landmark()));
		}
		if (request.city() != null) {
			profile.setCity(trimToNull(request.city()));
		}
		if (request.state() != null) {
			profile.setState(trimToNull(request.state()));
		}
		if (request.postalCode() != null) {
			profile.setPostalCode(upperCase(request.postalCode()));
		}
		if (request.countryCode() != null) {
			profile.setCountryCode(upperCase(request.countryCode()));
		}
		profile.setUpdatedBy(actingUserId);
	}

	public NexHomeAdminProfileResponse toAdminProfileDto(NexHomeAdminProfile profile) {
		return new NexHomeAdminProfileResponse(
				profile.getId(),
				profile.getPublicId(),
				profile.getUser().getId(),
				profile.getFirstName(),
				profile.getLastName(),
				profile.getEmployeeCode(),
				profile.getDepartment(),
				profile.getJobTitle(),
				profile.getAddressLine1(),
				profile.getAddressLine2(),
				profile.getLandmark(),
				profile.getCity(),
				profile.getState(),
				profile.getPostalCode(),
				profile.getCountryCode(),
				profile.getCreationDate());
	}
}
