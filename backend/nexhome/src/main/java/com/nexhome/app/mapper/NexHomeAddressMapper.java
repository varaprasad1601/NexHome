package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;
import static com.nexhome.app.mapper.NexHomeText.upperCase;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeAddressCreateRequest;
import com.nexhome.app.dto.NexHomeAddressResponse;
import com.nexhome.app.dto.NexHomeAddressUpdateRequest;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeAddressMapper {

	/**
	 * The country defaults to IN and coordinates are rounded to 7 decimals. The address type and default flag are
	 * applied by the service, which also checks the postal code for the country.
	 */
	public NexHomeAddress toAddressEntity(NexHomeAddressCreateRequest request, NexHomeUser user, Long actingUserId) {
		NexHomeAddress address = new NexHomeAddress();
		address.setUser(user);
		address.setContactName(trimToNull(request.contactName()));
		address.setMobileNumber(trimToNull(request.mobileNumber()));
		address.setLabel(trimToNull(request.label()));
		address.setAddressLine1(trimToNull(request.addressLine1()));
		address.setAddressLine2(trimToNull(request.addressLine2()));
		address.setLandmark(trimToNull(request.landmark()));
		address.setCity(trimToNull(request.city()));
		address.setState(trimToNull(request.state()));
		address.setPostalCode(upperCase(request.postalCode()));
		if (request.countryCode() != null) {
			address.setCountryCode(upperCase(request.countryCode()));
		}
		address.setLatitude(coordinate(request.latitude()));
		address.setLongitude(coordinate(request.longitude()));
		address.setCreatedBy(actingUserId);
		address.setUpdatedBy(actingUserId);
		return address;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty optional field is cleared. Coordinates are
	 * replaced only as a pair (the service rejects one without the other). The owner is never updated; the address
	 * type and default flag are applied by the service.
	 */
	public void updateAddressEntity(NexHomeAddress address, NexHomeAddressUpdateRequest request, Long actingUserId) {
		if (request.contactName() != null) {
			address.setContactName(trimToNull(request.contactName()));
		}
		if (request.mobileNumber() != null) {
			address.setMobileNumber(trimToNull(request.mobileNumber()));
		}
		if (request.label() != null) {
			address.setLabel(trimToNull(request.label()));
		}
		if (request.addressLine1() != null) {
			address.setAddressLine1(trimToNull(request.addressLine1()));
		}
		if (request.addressLine2() != null) {
			address.setAddressLine2(trimToNull(request.addressLine2()));
		}
		if (request.landmark() != null) {
			address.setLandmark(trimToNull(request.landmark()));
		}
		if (request.city() != null) {
			address.setCity(trimToNull(request.city()));
		}
		if (request.state() != null) {
			address.setState(trimToNull(request.state()));
		}
		if (request.postalCode() != null) {
			address.setPostalCode(upperCase(request.postalCode()));
		}
		if (request.countryCode() != null) {
			address.setCountryCode(upperCase(request.countryCode()));
		}
		if (request.latitude() != null && request.longitude() != null) {
			address.setLatitude(coordinate(request.latitude()));
			address.setLongitude(coordinate(request.longitude()));
		}
		address.setUpdatedBy(actingUserId);
	}

	public NexHomeAddressResponse toAddressDto(NexHomeAddress address) {
		return new NexHomeAddressResponse(
				address.getId(),
				address.getPublicId(),
				address.getUser().getId(),
				address.getContactName(),
				address.getMobileNumber(),
				address.getAddressType(),
				address.getLabel(),
				address.getAddressLine1(),
				address.getAddressLine2(),
				address.getLandmark(),
				address.getCity(),
				address.getState(),
				address.getPostalCode(),
				address.getCountryCode(),
				address.getLatitude(),
				address.getLongitude(),
				address.isDefaultAddress(),
				address.getCreationDate());
	}

	/** NUMERIC(10,7): GPS readings often carry more decimals than the column keeps. */
	static BigDecimal coordinate(BigDecimal value) {
		return value == null ? null : value.setScale(7, RoundingMode.HALF_UP);
	}
}
