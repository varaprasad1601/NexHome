package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeUserDetailsRequest;
import com.nexhome.app.dto.NexHomeUserDetailsResponse;
import com.nexhome.app.dto.NexHomeUserDetailsUpdateRequest;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserDetails;

@Component
public class NexHomeUserDetailsMapper {

	public NexHomeUserDetails toUserDetailsEntity(NexHomeUserDetailsRequest request, NexHomeUser user,
			Long actingUserId) {
		NexHomeUserDetails details = new NexHomeUserDetails();
		details.setUser(user);
		details.setFirstName(trimToNull(request.firstName()));
		details.setLastName(trimToNull(request.lastName()));
		details.setFullName(fullName(details.getFirstName(), details.getLastName()));
		details.setCreatedBy(actingUserId);
		details.setLastUpdateBy(actingUserId);
		return details;
	}

	/** Applies a partial update: null keeps the current value; a blank last name clears it. */
	public void updateUserDetailsEntity(NexHomeUserDetails details, NexHomeUserDetailsUpdateRequest request,
			Long actingUserId) {
		if (request.firstName() != null) {
			details.setFirstName(trimToNull(request.firstName()));
		}
		if (request.lastName() != null) {
			details.setLastName(trimToNull(request.lastName()));
		}
		details.setFullName(fullName(details.getFirstName(), details.getLastName()));
		details.setLastUpdateBy(actingUserId);
	}

	public NexHomeUserDetailsResponse toUserDetailsDto(NexHomeUserDetails details) {
		return new NexHomeUserDetailsResponse(
				details.getUserDetailsId(),
				details.getUser().getUserId(),
				details.getFirstName(),
				details.getLastName(),
				details.getFullName(),
				details.getCreationDate());
	}

	private static String fullName(String firstName, String lastName) {
		if (lastName == null) {
			return firstName;
		}
		return firstName == null ? lastName : firstName + " " + lastName;
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
