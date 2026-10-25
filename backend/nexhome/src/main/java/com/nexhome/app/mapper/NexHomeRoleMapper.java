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
		role.setCode(trimToNull(request.code()));
		role.setName(trimToNull(request.name()));
		role.setDescription(trimToNull(request.description()));
		role.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		role.setCreatedBy(actingUserId);
		role.setUpdatedBy(actingUserId);
		return role;
	}

	/**
	 * Applies an update: a null name or active flag keeps its current value; the optional description is always
	 * replaced, so null or blank clears it. The code is never updated.
	 */
	public void updateRoleEntity(NexHomeRole role, NexHomeRoleUpdateRequest request, Long actingUserId) {
		if (request.name() != null) {
			role.setName(trimToNull(request.name()));
		}
		role.setDescription(trimToNull(request.description()));
		if (request.activeFlag() != null) {
			role.setActiveFlag(request.activeFlag());
		}
		role.setUpdatedBy(actingUserId);
	}

	public NexHomeRoleResponse toRoleDto(NexHomeRole role) {
		return new NexHomeRoleResponse(
				role.getId(),
				role.getCode(),
				role.getName(),
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
