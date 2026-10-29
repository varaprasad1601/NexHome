package com.nexhome.app.controller;

import java.util.List;

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

import com.nexhome.app.dto.NexHomeAuditEntry;
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
 * profiles need CUSTOMER_MANAGE (customers) or ADMIN_MANAGE / SUB_ADMIN_MANAGE (staff, by the owner's role). Endpoints that take a profile id check the owner
 * in the service, after loading the profile.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class NexHomeProfileController {

	private final NexHomeProfileService profileService;

	@PostMapping("/customers/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'CUSTOMER_MANAGE', 'WRITE')")
	public NexHomeCustomerProfileResponse createCustomerProfile(
			@Valid @RequestBody NexHomeCustomerProfileCreateRequest request) {
		return profileService.createCustomerProfile(request);
	}

	/** Owner or CUSTOMER_MANAGE write. */
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
	@PreAuthorize("@permissions.can('CUSTOMER_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeCustomerProfileResponse> getCustomerProfiles(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String search) {
		return profileService.getCustomerProfiles(page, size, search);
	}

	/** The customer's activity log from the audit columns; owner or CUSTOMER_MANAGE read (checked in the service). */
	@GetMapping("/customers/activity/{customerProfileId}")
	public List<NexHomeAuditEntry> getCustomerProfileActivity(@PathVariable Long customerProfileId) {
		return profileService.getCustomerProfileActivity(customerProfileId);
	}

	/** Owner or CUSTOMER_MANAGE read. */
	@GetMapping("/customers/{customerProfileId}")
	public NexHomeCustomerProfileResponse getCustomerProfile(@PathVariable Long customerProfileId) {
		return profileService.getCustomerProfile(customerProfileId);
	}

	/**
	 * Staff may create their own profile; employee details and other users' profiles need write on the owner's staff
	 * role function (ADMIN_MANAGE, SUB_ADMIN_MANAGE; a super user's: super users). The service checks.
	 */
	@PostMapping("/admins/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("isAuthenticated()")
	public NexHomeAdminProfileResponse createAdminProfile(@Valid @RequestBody NexHomeAdminProfileCreateRequest request) {
		return profileService.createAdminProfile(request);
	}

	/** Owner (names and address) or write on the owner's staff role function. */
	@PutMapping("/admins/update")
	public NexHomeAdminProfileResponse updateAdminProfile(@Valid @RequestBody NexHomeAdminProfileUpdateRequest request) {
		return profileService.updateAdminProfile(request);
	}

	@GetMapping("/admins/me")
	public NexHomeAdminProfileResponse getCurrentAdminProfile() {
		return profileService.getCurrentAdminProfile();
	}

	/**
	 * Staff profiles of every staff role, newest first; same query parameters as /customers, and {@code search} also
	 * matches the employee code. Needs read on both ADMIN_MANAGE and SUB_ADMIN_MANAGE.
	 */
	@GetMapping("/admins")
	@PreAuthorize("@permissions.can('ADMIN_MANAGE', 'READ') and @permissions.can('SUB_ADMIN_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeAdminProfileResponse> getAdminProfiles(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String search) {
		return profileService.getAdminProfiles(page, size, search);
	}

	/** The staff member's activity log; owner or read on the owner's staff role function (checked in the service). */
	@GetMapping("/admins/activity/{adminProfileId}")
	public List<NexHomeAuditEntry> getAdminProfileActivity(@PathVariable Long adminProfileId) {
		return profileService.getAdminProfileActivity(adminProfileId);
	}

	/** Owner or read on the owner's staff role function. */
	@GetMapping("/admins/{adminProfileId}")
	public NexHomeAdminProfileResponse getAdminProfile(@PathVariable Long adminProfileId) {
		return profileService.getAdminProfile(adminProfileId);
	}
}
