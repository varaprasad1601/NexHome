package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

	/**
	 * Roles and their permissions follow ROLE_MANAGE: write to create, change and grant, delete to delete a role or a
	 * grant. The SUPER_USER role itself is changed by super users only (the service checks).
	 */
	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'WRITE')")
	public NexHomeRoleResponse createRole(@Valid @RequestBody NexHomeRoleCreateRequest request) {
		return roleService.createRole(request);
	}

	/** Every role, active or not (for the admin console's Roles screen). */
	@GetMapping
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'READ')")
	public List<NexHomeRoleResponse> getRoles() {
		return roleService.getRoles();
	}

	@GetMapping("/{roleId}")
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'READ')")
	public NexHomeRoleResponse getRole(@PathVariable Short roleId) {
		return roleService.getRole(roleId);
	}

	/** The role's permissions: the functions granted to it, with read / write / delete. */
	@GetMapping("/functions/{roleId}")
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'READ')")
	public List<NexHomeRoleFunctionResponse> getRoleFunctions(@PathVariable Short roleId) {
		return roleService.getRoleFunctions(roleId);
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'WRITE')")
	public NexHomeRoleResponse updateRole(@Valid @RequestBody NexHomeRoleUpdateRequest request) {
		return roleService.updateRole(request);
	}

	/** Only an unused role can be deleted (409 otherwise); deactivate a role that is in use. */
	@DeleteMapping("/delete/{roleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'DELETE')")
	public void deleteRole(@PathVariable Short roleId) {
		roleService.deleteRole(roleId);
	}

	@PostMapping("/functions/add")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'WRITE')")
	public NexHomeRoleFunctionResponse addRoleFunction(@Valid @RequestBody NexHomeRoleFunctionCreateRequest request) {
		return roleService.addRoleFunction(request);
	}

	@PutMapping("/functions/update")
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'WRITE')")
	public NexHomeRoleFunctionResponse updateRoleFunction(
			@Valid @RequestBody NexHomeRoleFunctionUpdateRequest request) {
		return roleService.updateRoleFunction(request);
	}

	@DeleteMapping("/functions/delete/{roleFunctionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('ROLE_MANAGE', 'DELETE')")
	public void deleteRoleFunction(@PathVariable Long roleFunctionId) {
		roleService.deleteRoleFunction(roleFunctionId);
	}
}
