package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeAddressCreateRequest;
import com.nexhome.app.dto.NexHomeAddressResponse;
import com.nexhome.app.dto.NexHomeAddressUpdateRequest;
import com.nexhome.app.service.NexHomeAddressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Customer service addresses. Customers manage their own; other users' addresses need PROFILE_MANAGE. Endpoints that
 * take an address id check the owner in the service, after loading the address.
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class NexHomeAddressController {

	private final NexHomeAddressService addressService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'PROFILE_MANAGE', 'WRITE')")
	public NexHomeAddressResponse createAddress(@Valid @RequestBody NexHomeAddressCreateRequest request) {
		return addressService.createAddress(request);
	}

	/** Owner or PROFILE_MANAGE write. */
	@PutMapping("/update")
	public NexHomeAddressResponse updateAddress(@Valid @RequestBody NexHomeAddressUpdateRequest request) {
		return addressService.updateAddress(request);
	}

	/** Owner or PROFILE_MANAGE delete. */
	@DeleteMapping("/delete/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAddress(@PathVariable Long addressId) {
		addressService.deleteAddress(addressId);
	}

	/** The caller's addresses, the default first. */
	@GetMapping("/me")
	public List<NexHomeAddressResponse> getCurrentUserAddresses() {
		return addressService.getCurrentUserAddresses();
	}

	/** A customer's addresses, the default first. */
	@GetMapping("/users/{userId}")
	@PreAuthorize("@permissions.isSelfOrCan(#userId, 'PROFILE_MANAGE', 'READ')")
	public List<NexHomeAddressResponse> getUserAddresses(@PathVariable Long userId) {
		return addressService.getUserAddresses(userId);
	}

	/** Owner or PROFILE_MANAGE read. */
	@GetMapping("/{addressId}")
	public NexHomeAddressResponse getAddress(@PathVariable Long addressId) {
		return addressService.getAddress(addressId);
	}
}
