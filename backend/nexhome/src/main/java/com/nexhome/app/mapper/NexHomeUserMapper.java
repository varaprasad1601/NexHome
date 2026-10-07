package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeUserMapper {

	public NexHomeUser toUserEntity(String phone, String email, String passwordHash, NexHomeRole role, Long actingUserId) {
		NexHomeUser user = new NexHomeUser();
		user.setPhone(phone);
		user.setEmail(email);
		user.setPassword(passwordHash);
		user.setRole(role);
		user.setActiveFlag(true);
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
				user.isActiveFlag(),
				user.getCreatedBy(),
				user.getCreationDate());
	}
}
