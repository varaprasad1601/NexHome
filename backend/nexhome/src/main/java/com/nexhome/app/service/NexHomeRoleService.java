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

	/**
	 * Deletes the role together with its role functions and every user with this role, including their details and
	 * addresses.
	 */
	void deleteRole(Long roleId);

	NexHomeRoleFunctionResponse addRoleFunction(NexHomeRoleFunctionCreateRequest request);

	NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request);

	void deleteRoleFunction(Long roleFunctionId);
}
