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

	/** read, write and delete default to false; write or delete also grants read. */
	public NexHomeRoleFunction toRoleFunctionEntity(NexHomeRoleFunctionCreateRequest request, NexHomeRole role,
			NexHomeFunction function, Long actingUserId) {
		NexHomeRoleFunction roleFunction = new NexHomeRoleFunction();
		roleFunction.setRole(role);
		roleFunction.setFunction(function);
		roleFunction.setRead(Boolean.TRUE.equals(request.read()));
		roleFunction.setWrite(Boolean.TRUE.equals(request.write()));
		roleFunction.setDelete(Boolean.TRUE.equals(request.delete()));
		grantReadWithWriteOrDelete(roleFunction);
		roleFunction.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		roleFunction.setCreatedBy(actingUserId);
		roleFunction.setLastUpdateBy(actingUserId);
		return roleFunction;
	}

	/** Applies an update: a null flag keeps its current value; write or delete also grants read. */
	public void updateRoleFunctionEntity(NexHomeRoleFunction roleFunction, NexHomeRoleFunctionUpdateRequest request,
			Long actingUserId) {
		if (request.read() != null) {
			roleFunction.setRead(request.read());
		}
		if (request.write() != null) {
			roleFunction.setWrite(request.write());
		}
		if (request.delete() != null) {
			roleFunction.setDelete(request.delete());
		}
		grantReadWithWriteOrDelete(roleFunction);
		if (request.activeFlag() != null) {
			roleFunction.setActiveFlag(request.activeFlag());
		}
		roleFunction.setLastUpdateBy(actingUserId);
	}

	public NexHomeRoleFunctionResponse toRoleFunctionDto(NexHomeRoleFunction roleFunction) {
		NexHomeRole role = roleFunction.getRole();
		NexHomeFunction function = roleFunction.getFunction();
		return new NexHomeRoleFunctionResponse(
				roleFunction.getRoleFunctionId(),
				role.getRoleId(),
				role.getRoleName(),
				function.getFunctionId(),
				function.getFunctionCode(),
				function.getFunctionName(),
				roleFunction.isRead(),
				roleFunction.isWrite(),
				roleFunction.isDelete(),
				roleFunction.isActiveFlag(),
				roleFunction.getCreationDate());
	}

	private static void grantReadWithWriteOrDelete(NexHomeRoleFunction roleFunction) {
		if (roleFunction.isWrite() || roleFunction.isDelete()) {
			roleFunction.setRead(true);
		}
	}
}
