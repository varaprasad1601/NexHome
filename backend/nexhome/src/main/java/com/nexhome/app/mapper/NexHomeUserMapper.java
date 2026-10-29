package com.nexhome.app.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeCustomerResponse;
import com.nexhome.app.dto.NexHomePartnerResponse;
import com.nexhome.app.dto.NexHomeStaffResponse;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleResponse;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeServiceCategory;
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

	/** A customer list row; {@code profile} is null while the customer has not created one. */
	public NexHomeCustomerResponse toCustomerDto(NexHomeUser user, List<NexHomeUserRole> userRoles,
			NexHomeCustomerProfile profile) {
		return new NexHomeCustomerResponse(
				user.getId(),
				profile == null ? null : profile.getId(),
				profile == null ? null : profile.getPublicId(),
				profile == null ? null : profile.getFirstName(),
				profile == null ? null : profile.getLastName(),
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

	/** A partner list row; {@code profile} is null until the partner starts their onboarding profile. */
	public NexHomePartnerResponse toPartnerDto(NexHomeUser user, List<NexHomeUserRole> userRoles,
			NexHomePartnerProfile profile) {
		NexHomeServiceCategory category = profile == null ? null : profile.getMainServiceCategory();
		return new NexHomePartnerResponse(
				user.getId(),
				profile == null ? null : profile.getId(),
				profile == null ? null : profile.getPublicId(),
				profile == null ? null : profile.getFirstName(),
				profile == null ? null : profile.getLastName(),
				user.getCountryCode(),
				user.getMobileNumber(),
				user.getEmail(),
				user.getProfilePhotoUrl(),
				user.getAccountStatus(),
				user.getAccountVerifiedDate(),
				user.getLastLoginAt(),
				profile == null ? null : profile.getCity(),
				category == null ? null : category.getId(),
				category == null ? null : category.getName(),
				profile == null ? null : profile.getOnboardingStatus(),
				profile == null ? null : profile.getApprovalStatus(),
				profile == null ? null : profile.getAvailabilityStatus(),
				userRoles.stream().map(NexHomeUserMapper::toUserRoleDto).toList(),
				user.getCreationDate());
	}

	/** A staff list row; {@code role} is the account's highest staff role and {@code profile} may be null. */
	public NexHomeStaffResponse toStaffDto(NexHomeUser user, NexHomeRole role, NexHomeAdminProfile profile) {
		return new NexHomeStaffResponse(
				user.getId(),
				profile == null ? null : profile.getId(),
				profile == null ? null : profile.getPublicId(),
				profile == null ? null : profile.getFirstName(),
				profile == null ? null : profile.getLastName(),
				user.getEmail(),
				user.getCountryCode(),
				user.getMobileNumber(),
				role == null ? null : role.getCode(),
				role == null ? null : role.getName(),
				user.getAccountStatus(),
				user.getLastLoginAt(),
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
