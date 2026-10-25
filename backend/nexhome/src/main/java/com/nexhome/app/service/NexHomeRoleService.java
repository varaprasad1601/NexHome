package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;

public interface NexHomeRoleService {

	NexHomeRoleResponse createRole(NexHomeRoleCreateRequest request);

	NexHomeRoleResponse updateRole(NexHomeRoleUpdateRequest request);

	/** Deletes an unused role. A role assigned to users or granted functions must be deactivated instead. */
	void deleteRole(Short roleId);

	NexHomeRoleFunctionResponse addRoleFunction(NexHomeRoleFunctionCreateRequest request);

	NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request);

	void deleteRoleFunction(Long roleFunctionId);
}
