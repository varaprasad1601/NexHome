package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeRoleFunction;

@Component
public class NexHomeRoleFunctionMapper {

	/** canRead, canWrite and canDelete default to false; write or delete also grants read. */
	public NexHomeRoleFunction toRoleFunctionEntity(NexHomeRoleFunctionCreateRequest request, NexHomeRole role,
			NexHomeFunction function, Long actingUserId) {
		NexHomeRoleFunction roleFunction = new NexHomeRoleFunction();
		roleFunction.setRole(role);
		roleFunction.setFunction(function);
		roleFunction.setCanRead(Boolean.TRUE.equals(request.canRead()));
		roleFunction.setCanWrite(Boolean.TRUE.equals(request.canWrite()));
		roleFunction.setCanDelete(Boolean.TRUE.equals(request.canDelete()));
		grantReadWithWriteOrDelete(roleFunction);
		roleFunction.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		roleFunction.setCreatedBy(actingUserId);
		roleFunction.setUpdatedBy(actingUserId);
		return roleFunction;
	}

	/** Applies an update: a null flag keeps its current value; write or delete also grants read. */
	public void updateRoleFunctionEntity(NexHomeRoleFunction roleFunction, NexHomeRoleFunctionUpdateRequest request,
			Long actingUserId) {
		if (request.canRead() != null) {
			roleFunction.setCanRead(request.canRead());
		}
		if (request.canWrite() != null) {
			roleFunction.setCanWrite(request.canWrite());
		}
		if (request.canDelete() != null) {
			roleFunction.setCanDelete(request.canDelete());
		}
		grantReadWithWriteOrDelete(roleFunction);
		if (request.activeFlag() != null) {
			roleFunction.setActiveFlag(request.activeFlag());
		}
		roleFunction.setUpdatedBy(actingUserId);
	}

	public NexHomeRoleFunctionResponse toRoleFunctionDto(NexHomeRoleFunction roleFunction) {
		NexHomeRole role = roleFunction.getRole();
		NexHomeFunction function = roleFunction.getFunction();
		return new NexHomeRoleFunctionResponse(
				roleFunction.getId(),
				role.getId(),
				role.getCode(),
				function.getId(),
				function.getFunctionCode(),
				function.getFunctionName(),
				roleFunction.isCanRead(),
				roleFunction.isCanWrite(),
				roleFunction.isCanDelete(),
				roleFunction.isActiveFlag(),
				roleFunction.getCreationDate());
	}

	private static void grantReadWithWriteOrDelete(NexHomeRoleFunction roleFunction) {
		if (roleFunction.isCanWrite() || roleFunction.isCanDelete()) {
			roleFunction.setCanRead(true);
		}
	}
}
