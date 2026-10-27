package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.collapseWhitespace;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeCustomerProfileCreateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileResponse;
import com.nexhome.app.dto.NexHomeCustomerProfileUpdateRequest;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeCustomerProfileMapper {

	/** The preferred language is validated and applied by the service. */
	public NexHomeCustomerProfile toCustomerProfileEntity(NexHomeCustomerProfileCreateRequest request, NexHomeUser user,
			Long actingUserId) {
		NexHomeCustomerProfile profile = new NexHomeCustomerProfile();
		profile.setUser(user);
		profile.setFirstName(collapseWhitespace(request.firstName()));
		profile.setLastName(collapseWhitespace(request.lastName()));
		profile.setDateOfBirth(request.dateOfBirth());
		profile.setMarketingConsent(Boolean.TRUE.equals(request.marketingConsent()));
		profile.setCreatedBy(actingUserId);
		profile.setUpdatedBy(actingUserId);
		return profile;
	}

	/** The empty profile every customer account gets (V15), with its NHCUST public id; the customer adds the rest. */
	public NexHomeCustomerProfile toCustomerProfileEntity(NexHomeUser user, Long actingUserId) {
		NexHomeCustomerProfile profile = new NexHomeCustomerProfile();
		profile.setUser(user);
		profile.setCreatedBy(actingUserId);
		profile.setUpdatedBy(actingUserId);
		return profile;
	}

	/** Fills in a customer's empty profile (created with the account) from a create request. */
	public void fillCustomerProfileEntity(NexHomeCustomerProfile profile, NexHomeCustomerProfileCreateRequest request,
			Long actingUserId) {
		profile.setFirstName(collapseWhitespace(request.firstName()));
		profile.setLastName(collapseWhitespace(request.lastName()));
		profile.setDateOfBirth(request.dateOfBirth());
		profile.setMarketingConsent(Boolean.TRUE.equals(request.marketingConsent()));
		profile.setUpdatedBy(actingUserId);
	}

	/**
	 * Applies an update: a null field keeps its current value. The owner is never updated; the preferred language is
	 * applied by the service.
	 */
	public void updateCustomerProfileEntity(NexHomeCustomerProfile profile, NexHomeCustomerProfileUpdateRequest request,
			Long actingUserId) {
		if (request.firstName() != null) {
			profile.setFirstName(collapseWhitespace(request.firstName()));
		}
		if (request.lastName() != null) {
			profile.setLastName(collapseWhitespace(request.lastName()));
		}
		if (request.dateOfBirth() != null) {
			profile.setDateOfBirth(request.dateOfBirth());
		}
		if (request.marketingConsent() != null) {
			profile.setMarketingConsent(request.marketingConsent());
		}
		profile.setUpdatedBy(actingUserId);
	}

	public NexHomeCustomerProfileResponse toCustomerProfileDto(NexHomeCustomerProfile profile) {
		return new NexHomeCustomerProfileResponse(
				profile.getId(),
				profile.getPublicId(),
				profile.getUser().getId(),
				profile.getFirstName(),
				profile.getLastName(),
				profile.getDateOfBirth(),
				profile.getPreferredLanguage(),
				profile.isMarketingConsent(),
				profile.getCreationDate());
	}
}
