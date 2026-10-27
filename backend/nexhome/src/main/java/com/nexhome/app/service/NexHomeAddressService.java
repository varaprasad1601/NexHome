package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeAddressCreateRequest;
import com.nexhome.app.dto.NexHomeAddressResponse;
import com.nexhome.app.dto.NexHomeAddressUpdateRequest;

/**
 * Customer service addresses. Customers manage their own; acting on someone else's needs CUSTOMER_MANAGE.
 */
public interface NexHomeAddressService {

	/**
	 * Adds an address for an account holding the CUSTOMER role. It becomes the default when {@code isDefault} is true,
	 * or when it is not given and the customer has no default yet.
	 */
	NexHomeAddressResponse createAddress(NexHomeAddressCreateRequest request);

	/** Owner or CUSTOMER_MANAGE write. */
	NexHomeAddressResponse updateAddress(NexHomeAddressUpdateRequest request);

	/** Owner or CUSTOMER_MANAGE delete. */
	void deleteAddress(Long addressId);

	/** Owner or CUSTOMER_MANAGE read. */
	NexHomeAddressResponse getAddress(Long addressId);

	/** A customer's addresses, the default first, then newest first. */
	List<NexHomeAddressResponse> getUserAddresses(Long userId);

	/** The caller's addresses. */
	List<NexHomeAddressResponse> getCurrentUserAddresses();
}
