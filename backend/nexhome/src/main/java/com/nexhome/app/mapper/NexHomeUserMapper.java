package com.nexhome.app.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleResponse;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.model.NexHomeUserRoleId;

@Component
public class NexHomeUserMapper {

	/** A new account is ACTIVE; its mobile number is unverified until the first REGISTRATION or LOGIN OTP. */
	public NexHomeUser toUserEntity(String countryCode, String mobileNumber, String email, String profilePhotoUrl,
			Long actingUserId) {
		NexHomeUser user = new NexHomeUser();
		user.setCountryCode(countryCode);
		user.setMobileNumber(mobileNumber);
		user.setEmail(email);
		user.setProfilePhotoUrl(profilePhotoUrl);
		user.setAccountStatus(AccountStatus.ACTIVE);
		user.setCreatedBy(actingUserId);
		user.setUpdatedBy(actingUserId);
		return user;
	}

	public NexHomeUserRole toUserRoleEntity(NexHomeUser user, NexHomeRole role, Long actingUserId) {
		NexHomeUserRole userRole = new NexHomeUserRole();
		userRole.setId(new NexHomeUserRoleId(user.getId(), role.getId()));
		userRole.setUser(user);
		userRole.setRole(role);
		userRole.setActive(true);
		userRole.setAssignedBy(actingUserId);
		userRole.setCreatedBy(actingUserId);
		userRole.setUpdatedBy(actingUserId);
		return userRole;
	}

	public NexHomeUserResponse toUserDto(NexHomeUser user, List<NexHomeUserRole> userRoles) {
		return new NexHomeUserResponse(
				user.getId(),
				user.getCountryCode(),
				user.getMobileNumber(),
				user.getEmail(),
				user.getProfilePhotoUrl(),
				user.getAccountStatus(),
				user.getAccountVerifiedDate(),
				user.getLastLoginAt(),
				userRoles.stream().map(NexHomeUserMapper::toUserRoleDto).toList(),
				user.getCreationDate());
	}

	private static NexHomeUserRoleResponse toUserRoleDto(NexHomeUserRole userRole) {
		NexHomeRole role = userRole.getRole();
		return new NexHomeUserRoleResponse(
				role.getId(),
				role.getCode(),
				role.getName(),
				userRole.isActive(),
				userRole.getAssignedAt());
	}
}
