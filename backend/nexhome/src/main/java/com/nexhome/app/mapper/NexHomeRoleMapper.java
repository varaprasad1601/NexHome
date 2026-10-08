package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;
import com.nexhome.app.model.NexHomeRole;

@Component
public class NexHomeRoleMapper {

	public NexHomeRole toRoleEntity(NexHomeRoleCreateRequest request, Long actingUserId) {
		NexHomeRole role = new NexHomeRole();
		role.setRoleName(trimToNull(request.roleName()));
		role.setDescription(trimToNull(request.description()));
		role.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		role.setCreatedBy(actingUserId);
		role.setLastUpdateBy(actingUserId);
		return role;
	}

	/**
	 * Applies an update: the optional description is always replaced, so null or blank clears it. The role name is
	 * never updated.
	 */
	public void updateRoleEntity(NexHomeRole role, NexHomeRoleUpdateRequest request, Long actingUserId) {
		role.setDescription(trimToNull(request.description()));
		if (request.activeFlag() != null) {
			role.setActiveFlag(request.activeFlag());
		}
		role.setLastUpdateBy(actingUserId);
	}

	public NexHomeRoleResponse toRoleDto(NexHomeRole role) {
		return new NexHomeRoleResponse(
				role.getRoleId(),
				role.getRoleName(),
				role.getDescription(),
				role.isActiveFlag(),
				role.getCreationDate());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
