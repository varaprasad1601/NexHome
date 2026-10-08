package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressResponse;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.dto.NexHomeUserDetailsRequest;
import com.nexhome.app.dto.NexHomeUserDetailsResponse;
import com.nexhome.app.dto.NexHomeUserDetailsUpdateRequest;
import com.nexhome.app.dto.NexHomeUserPasswordSetRequest;
import com.nexhome.app.dto.NexHomeUserPasswordUpdateRequest;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;

public interface NexHomeUserService {

	NexHomeUserResponse createUser(NexHomeUserCreateRequest request);

	NexHomeUserDetailsResponse addUserDetails(NexHomeUserDetailsRequest request);

	NexHomeUserAddressResponse addUserAddress(NexHomeUserAddressRequest request);

	NexHomeUserResponse updateUser(NexHomeUserUpdateRequest request);

	NexHomeUserResponse updatePassword(NexHomeUserPasswordUpdateRequest request);

	/** Admin decision: SUBMITTED or REJECTED -> APPROVED, SUBMITTED or APPROVED -> REJECTED. */
	NexHomeUserResponse updateUserStatus(NexHomeUserStatusUpdateRequest request);

	/** First password of an APPROVED user; makes the user ACTIVATED and active. */
	NexHomeUserResponse setPassword(NexHomeUserPasswordSetRequest request);

	/** Deletes the user together with their details and addresses. */
	void deleteUser(Long userId);

	NexHomeUserDetailsResponse updateUserDetails(NexHomeUserDetailsUpdateRequest request);

	NexHomeUserAddressResponse updateUserAddress(NexHomeUserAddressUpdateRequest request);

	/** Deletes one address. Deleting the active address leaves the user without one; none is activated instead. */
	void deleteUserAddress(Long userAddressId);
}
