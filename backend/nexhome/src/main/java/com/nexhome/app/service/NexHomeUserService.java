package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;

public interface NexHomeUserService {

	/** Creates an ACTIVE account for a new mobile number, with one role (staff accounts; customers self-register). */
	NexHomeUserResponse createUser(NexHomeUserCreateRequest request);

	NexHomeUserResponse getUser(Long userId);

	/** The signed-in user. */
	NexHomeUserResponse getCurrentUser();

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
