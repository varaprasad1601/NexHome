package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;
import com.nexhome.app.service.NexHomeRoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class NexHomeRoleController {

	private final NexHomeRoleService roleService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeRoleResponse createRole(@Valid @RequestBody NexHomeRoleCreateRequest request) {
		return roleService.createRole(request);
	}

	@PutMapping("/update")
	public NexHomeRoleResponse updateRole(@Valid @RequestBody NexHomeRoleUpdateRequest request) {
		return roleService.updateRole(request);
	}

	/** Also deletes the role's role functions and every user with this role, with their details and addresses. */
	@DeleteMapping("/delete/{roleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRole(@PathVariable Long roleId) {
		roleService.deleteRole(roleId);
	}

	@PostMapping("/functions/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeRoleFunctionResponse addRoleFunction(@Valid @RequestBody NexHomeRoleFunctionCreateRequest request) {
		return roleService.addRoleFunction(request);
	}

	@PutMapping("/functions/update")
	public NexHomeRoleFunctionResponse updateRoleFunction(
			@Valid @RequestBody NexHomeRoleFunctionUpdateRequest request) {
		return roleService.updateRoleFunction(request);
	}

	@DeleteMapping("/functions/delete/{roleFunctionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRoleFunction(@PathVariable Long roleFunctionId) {
		roleService.deleteRoleFunction(roleFunctionId);
	}
}
