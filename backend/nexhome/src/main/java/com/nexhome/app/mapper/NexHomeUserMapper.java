package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserStatus;

@Component
public class NexHomeUserMapper {

	/** A new user is SUBMITTED, inactive and has no password until an admin approves them and they set one. */
	public NexHomeUser toUserEntity(String phone, String email, NexHomeRole role, Long actingUserId) {
		NexHomeUser user = new NexHomeUser();
		user.setPhone(phone);
		user.setEmail(email);
		user.setRole(role);
		user.setUserStatus(NexHomeUserStatus.SUBMITTED);
		user.setActiveFlag(false);
		user.setCreatedBy(actingUserId);
		user.setLastUpdateBy(actingUserId);
		return user;
	}

	public NexHomeUserResponse toUserDto(NexHomeUser user) {
		NexHomeRole role = user.getRole();
		return new NexHomeUserResponse(
				user.getUserId(),
				user.getPhone(),
				user.getEmail(),
				role.getRoleName(),
				user.getUserStatus(),
				user.isActiveFlag(),
				user.getCreationDate());
	}
}
