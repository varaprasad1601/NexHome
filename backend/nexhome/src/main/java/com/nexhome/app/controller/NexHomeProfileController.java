package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeAdminProfileCreateRequest;
import com.nexhome.app.dto.NexHomeAdminProfileResponse;
import com.nexhome.app.dto.NexHomeAdminProfileUpdateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileCreateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileResponse;
import com.nexhome.app.dto.NexHomeCustomerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.service.NexHomeProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Customer profiles and staff (admin / sub-admin / super user) profiles. Owners manage their own; other users'
 * profiles need PROFILE_MANAGE (customers) or STAFF_MANAGE (staff). Endpoints that take a profile id check the owner
 * in the service, after loading the profile.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class NexHomeProfileController {

	private final NexHomeProfileService profileService;

	@PostMapping("/customers/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'PROFILE_MANAGE', 'WRITE')")
	public NexHomeCustomerProfileResponse createCustomerProfile(
			@Valid @RequestBody NexHomeCustomerProfileCreateRequest request) {
		return profileService.createCustomerProfile(request);
	}

	/** Owner or PROFILE_MANAGE write. */
	@PutMapping("/customers/update")
	public NexHomeCustomerProfileResponse updateCustomerProfile(
			@Valid @RequestBody NexHomeCustomerProfileUpdateRequest request) {
		return profileService.updateCustomerProfile(request);
	}

	@GetMapping("/customers/me")
	public NexHomeCustomerProfileResponse getCurrentCustomerProfile() {
		return profileService.getCurrentCustomerProfile();
	}

	/**
	 * Customer profiles, newest first. Query parameters (all optional): {@code page} (0-based), {@code size} (1-100,
	 * default 10), {@code search} (part of the first or last name or the public id).
	 */
	@GetMapping("/customers")
	@PreAuthorize("@permissions.can('PROFILE_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeCustomerProfileResponse> getCustomerProfiles(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String search) {
		return profileService.getCustomerProfiles(page, size, search);
	}

	/** Owner or PROFILE_MANAGE read. */
	@GetMapping("/customers/{customerProfileId}")
	public NexHomeCustomerProfileResponse getCustomerProfile(@PathVariable Long customerProfileId) {
		return profileService.getCustomerProfile(customerProfileId);
	}

	/** Staff may create their own profile; employee details and other users' profiles need STAFF_MANAGE write. */
	@PostMapping("/admins/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'STAFF_MANAGE', 'WRITE')")
	public NexHomeAdminProfileResponse createAdminProfile(@Valid @RequestBody NexHomeAdminProfileCreateRequest request) {
		return profileService.createAdminProfile(request);
	}

	/** Owner (names and address) or STAFF_MANAGE write. */
	@PutMapping("/admins/update")
	public NexHomeAdminProfileResponse updateAdminProfile(@Valid @RequestBody NexHomeAdminProfileUpdateRequest request) {
		return profileService.updateAdminProfile(request);
	}

	@GetMapping("/admins/me")
	public NexHomeAdminProfileResponse getCurrentAdminProfile() {
		return profileService.getCurrentAdminProfile();
	}

	/**
	 * Staff profiles, newest first; same query parameters as /customers, and {@code search} also matches the
	 * employee code.
	 */
	@GetMapping("/admins")
	@PreAuthorize("@permissions.can('STAFF_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeAdminProfileResponse> getAdminProfiles(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String search) {
		return profileService.getAdminProfiles(page, size, search);
	}

	/** Owner or STAFF_MANAGE read. */
	@GetMapping("/admins/{adminProfileId}")
	public NexHomeAdminProfileResponse getAdminProfile(@PathVariable Long adminProfileId) {
		return profileService.getAdminProfile(adminProfileId);
	}
}
