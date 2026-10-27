package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeCustomerResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePartnerResponse;
import com.nexhome.app.dto.NexHomePartnerStatsResponse;
import com.nexhome.app.dto.NexHomeStaffResponse;
import com.nexhome.app.dto.NexHomeStaffStatsResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatsResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;

public interface NexHomeUserService {

	/**
	 * Creates an ACTIVE account for a new mobile number, with one role (staff accounts; customers self-register). A
	 * SUPER_USER, ADMIN or SUB_ADMIN account also gets its empty staff profile, with its public id.
	 */
	NexHomeUserResponse createUser(NexHomeUserCreateRequest request);

	NexHomeUserResponse getUser(Long userId);

	/** The signed-in user. */
	NexHomeUserResponse getCurrentUser();

	/**
	 * Current CUSTOMER holders with their customer profile, newest first; {@code search} also matches the profile's
	 * name and public id.
	 */
	NexHomePageResponse<NexHomeCustomerResponse> getCustomers(Integer page, Integer size, String accountStatus,
			String search);

	/** Customer counts by account status. */
	NexHomeStaffStatsResponse getCustomerStats();

	/**
	 * Current PARTNER holders with their onboarding profile, newest first. {@code onboardingStatus} is a
	 * PARTNER_ONBOARDING_STATUS code or NOT_STARTED (no profile yet); {@code search} also matches the profile's name
	 * and public id.
	 */
	NexHomePageResponse<NexHomePartnerResponse> getPartners(Integer page, Integer size, String accountStatus,
			String onboardingStatus, String search);

	NexHomePartnerStatsResponse getPartnerStats();

	/**
	 * One page of the users who currently hold the role (active assignment), newest first.
	 *
	 * @param page zero-based; defaults to 0
	 * @param size 1 to 100; defaults to 10
	 * @param accountStatus optional ACCOUNT_STATUS code filter
	 * @param search optional text contained in the mobile number or email
	 */
	NexHomePageResponse<NexHomeUserResponse> getUsersByRole(String roleCode, Integer page, Integer size,
			String accountStatus, String search);

	/**
	 * One page of staff accounts (current SUPER_USER, ADMIN or SUB_ADMIN holders) with their staff profile names,
	 * newest first. Super users are left out unless the caller is one.
	 *
	 * @param roleCode optional: only holders of this staff role
	 * @param accountStatus optional ACCOUNT_STATUS code
	 * @param search optional text contained in the mobile number, email, name or profile public id
	 */
	NexHomePageResponse<NexHomeStaffResponse> getStaff(Integer page, Integer size, String roleCode,
			String accountStatus, String search);

	/** Staff accounts the caller may see, by account status; {@code roleCode} optionally limits them to one role. */
	NexHomeStaffStatsResponse getStaffStats(String roleCode);

	/** Customer and partner totals, this month's additions and the last 10 months, for the admin dashboard. */
	NexHomeUserStatsResponse getUserStats();

	NexHomeUserResponse updateUser(NexHomeUserUpdateRequest request);

	/**
	 * Admin: sets the account status to an active ACCOUNT_STATUS code. Users are deactivated, never deleted; leaving
	 * ACTIVE ends all their login sessions. Nobody can change their own status, and only a super user can change a
	 * super user's.
	 */
	NexHomeUserResponse updateUserStatus(NexHomeUserStatusUpdateRequest request);

	/** Gives the user another role (a user may have several). Only a super user can grant SUPER_USER. */
	NexHomeUserResponse assignRole(NexHomeUserRoleAssignRequest request);

	/** Activates or withdraws a role the user already has. Only a super user can change a SUPER_USER assignment. */
	NexHomeUserResponse updateUserRole(NexHomeUserRoleUpdateRequest request);
}
