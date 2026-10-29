package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeAuditEntry;
import com.nexhome.app.dto.NexHomeAdminProfileCreateRequest;
import com.nexhome.app.dto.NexHomeAdminProfileResponse;
import com.nexhome.app.dto.NexHomeAdminProfileUpdateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileCreateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileResponse;
import com.nexhome.app.dto.NexHomeCustomerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;

/**
 * Customer profiles and staff (admin) profiles. Owners manage their own profile; acting on someone else's needs
 * CUSTOMER_MANAGE (customers) or ADMIN_MANAGE / SUB_ADMIN_MANAGE (staff, by the owner's role).
 */
public interface NexHomeProfileService {

	/** Creates the profile of an account holding the CUSTOMER role; one per account. */
	NexHomeCustomerProfileResponse createCustomerProfile(NexHomeCustomerProfileCreateRequest request);

	/** Owner or CUSTOMER_MANAGE write. */
	NexHomeCustomerProfileResponse updateCustomerProfile(NexHomeCustomerProfileUpdateRequest request);

	/** Owner or CUSTOMER_MANAGE read. */
	NexHomeCustomerProfileResponse getCustomerProfile(Long customerProfileId);

	/**
	 * The customer's activity log from the audit columns (account, profile, addresses), newest first; owner or
	 * CUSTOMER_MANAGE read.
	 */
	List<NexHomeAuditEntry> getCustomerProfileActivity(Long customerProfileId);

	/** The caller's customer profile. */
	NexHomeCustomerProfileResponse getCurrentCustomerProfile();

	/**
	 * One page of customer profiles, newest first.
	 *
	 * @param search optional text contained in the first or last name or the public id
	 */
	NexHomePageResponse<NexHomeCustomerProfileResponse> getCustomerProfiles(Integer page, Integer size, String search);

	/**
	 * Creates the profile of an account holding SUPER_USER, ADMIN or SUB_ADMIN; one per account. Staff may create
	 * their own without employee details; the employee code, department and job title, and anyone else's profile, need
	 * ADMIN_MANAGE / SUB_ADMIN_MANAGE write (by the owner's role). Only a super user can create a super user's profile.
	 */
	NexHomeAdminProfileResponse createAdminProfile(NexHomeAdminProfileCreateRequest request);

	/** Same rules as {@link #createAdminProfile}. */
	NexHomeAdminProfileResponse updateAdminProfile(NexHomeAdminProfileUpdateRequest request);

	/** Owner or read on the owner's staff role function. */
	NexHomeAdminProfileResponse getAdminProfile(Long adminProfileId);

	/** The staff member's activity log (account, profile), newest first; owner or the account function's read. */
	List<NexHomeAuditEntry> getAdminProfileActivity(Long adminProfileId);

	/** The caller's staff profile. */
	NexHomeAdminProfileResponse getCurrentAdminProfile();

	/**
	 * One page of staff profiles, newest first.
	 *
	 * @param search optional text contained in the first or last name, the public id or the employee code
	 */
	NexHomePageResponse<NexHomeAdminProfileResponse> getAdminProfiles(Integer page, Integer size, String search);
}
