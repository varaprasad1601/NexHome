package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;

public interface NexHomeRoleService {

	NexHomeRoleResponse createRole(NexHomeRoleCreateRequest request);

	NexHomeRoleResponse updateRole(NexHomeRoleUpdateRequest request);

	/** Every role, active or not, in id order (the seeded roles first). */
	List<NexHomeRoleResponse> getRoles();

	NexHomeRoleResponse getRole(Short roleId);

	/** The functions granted to a role (active or not), by function module and code. */
	List<NexHomeRoleFunctionResponse> getRoleFunctions(Short roleId);

	/** Deletes an unused role. A role assigned to users or granted functions must be deactivated instead. */
	void deleteRole(Short roleId);

	NexHomeRoleFunctionResponse addRoleFunction(NexHomeRoleFunctionCreateRequest request);

	NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request);

	void deleteRoleFunction(Long roleFunctionId);
}
