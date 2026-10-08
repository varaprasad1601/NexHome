package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressResponse;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserAddress;

@Component
public class NexHomeUserAddressMapper {

	public NexHomeUserAddress toUserAddressEntity(NexHomeUserAddressRequest request, NexHomeUser user,
			boolean active, Long actingUserId) {
		NexHomeUserAddress address = new NexHomeUserAddress();
		address.setUser(user);
		address.setAddress1(trimToNull(request.address1()));
		address.setAddress2(trimToNull(request.address2()));
		address.setAddress3(trimToNull(request.address3()));
		address.setCity(trimToNull(request.city()));
		address.setDistrict(trimToNull(request.district()));
		address.setState(trimToNull(request.state()));
		address.setPinCode(trimToNull(request.pinCode()));
		address.setActiveFlag(active);
		address.setCreatedBy(actingUserId);
		address.setLastUpdateBy(actingUserId);
		return address;
	}

	/**
	 * Applies an address update. Required fields keep their current value when null; the optional address3 is
	 * always replaced, so null or blank clears it. The active flag is handled by the service.
	 */
	public void updateUserAddressEntity(NexHomeUserAddress address, NexHomeUserAddressUpdateRequest request,
			Long actingUserId) {
		if (request.address1() != null) {
			address.setAddress1(trimToNull(request.address1()));
		}
		if (request.address2() != null) {
			address.setAddress2(trimToNull(request.address2()));
		}
		address.setAddress3(trimToNull(request.address3()));
		if (request.city() != null) {
			address.setCity(trimToNull(request.city()));
		}
		if (request.district() != null) {
			address.setDistrict(trimToNull(request.district()));
		}
		if (request.state() != null) {
			address.setState(trimToNull(request.state()));
		}
		if (request.pinCode() != null) {
			address.setPinCode(trimToNull(request.pinCode()));
		}
		address.setLastUpdateBy(actingUserId);
	}

	public NexHomeUserAddressResponse toUserAddressDto(NexHomeUserAddress address) {
		return new NexHomeUserAddressResponse(
				address.getUserAddressId(),
				address.getUser().getUserId(),
				address.getAddress1(),
				address.getAddress2(),
				address.getAddress3(),
				address.getCity(),
				address.getDistrict(),
				address.getState(),
				address.getPinCode(),
				address.isActiveFlag(),
				address.getCreationDate());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
