package com.nexhome.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeRoleFunctionMapper;
import com.nexhome.app.mapper.NexHomeRoleMapper;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeRoleFunction;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeRoleServiceImpl implements NexHomeRoleService {

	private final NexHomePermissionService permissionService;
	private final NexHomeAuditRecorder auditRecorder;
	private final NexHomeRoleRepository roleRepository;
	private final NexHomeFunctionRepository functionRepository;
	private final NexHomeRoleFunctionRepository roleFunctionRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeRoleMapper roleMapper;
	private final NexHomeRoleFunctionMapper roleFunctionMapper;

	@Override
	@Transactional
	public NexHomeRoleResponse createRole(NexHomeRoleCreateRequest request) {
		if (roleRepository.findByCode(request.code().trim()).isPresent()) {
			throw new DuplicateResourceException("Role code already exists");
		}

		NexHomeRole role = roleRepository.save(roleMapper.toRoleEntity(request, permissionService.currentUserId()));

		auditRecorder.record("CREATED", "ROLE", role.getCode(), null, roleValues(role),
				permissionService.currentUserId());
		log.info("Role created with Role ID {} ({})", role.getId(), role.getCode());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional
	public NexHomeRoleResponse updateRole(NexHomeRoleUpdateRequest request) {
		NexHomeRole role = roleRepository.findById(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));

		requireSuperUserForSuperUserRole(role);
		// The code is never updated (the request rejects it, and the column is not updatable)
		Map<String, Object> before = roleValues(role);
		roleMapper.updateRoleEntity(role, request, permissionService.currentUserId());
		auditRecorder.record("UPDATED", "ROLE", role.getCode(), before, roleValues(role),
				permissionService.currentUserId());

		log.info("Role updated with Role ID {}", role.getId());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeRoleResponse> getRoles() {
		return roleRepository.findAllByOrderByIdAsc().stream()
				.map(roleMapper::toRoleDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeRoleResponse getRole(Short roleId) {
		return roleMapper.toRoleDto(roleRepository.findById(roleId)
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist.")));
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeRoleFunctionResponse> getRoleFunctions(Short roleId) {
		if (!roleRepository.existsById(roleId)) {
			throw new ResourceNotFoundException("Role does not exist.");
		}
		return roleFunctionRepository.findByRoleId(roleId).stream()
				.sorted(Comparator.comparing((NexHomeRoleFunction rf) -> rf.getFunction().getFunctionModule())
						.thenComparing(rf -> rf.getFunction().getFunctionCode()))
				.map(roleFunctionMapper::toRoleFunctionDto)
				.toList();
	}

	@Override
	@Transactional
	public void deleteRole(Short roleId) {
		NexHomeRole role = roleRepository.findById(roleId)
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));
		requireSuperUserForSuperUserRole(role);
		// User roles and role functions reference the role with ON DELETE RESTRICT: deactivate a role in use
		if (userRoleRepository.existsByRoleId(roleId) || roleFunctionRepository.existsByRoleId(roleId)) {
			throw new DuplicateResourceException("Role is assigned to users or functions; deactivate it instead.");
		}
		auditRecorder.record("DELETED", "ROLE", role.getCode(), roleValues(role), null,
				permissionService.currentUserId());
		roleRepository.deleteById(roleId);

		log.info("Role deleted with Role ID {}", roleId);
	}

	@Override
	@Transactional
	public NexHomeRoleFunctionResponse addRoleFunction(NexHomeRoleFunctionCreateRequest request) {
		NexHomeRole role = roleRepository.findById(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));
		NexHomeFunction function = functionRepository.findById(request.functionId())
				.orElseThrow(() -> new ResourceNotFoundException("Function does not exist."));
		requireSuperUserForSuperUserRole(role);

		// (role_id, function_id) is unique in nexhome_role_functions
		if (roleFunctionRepository.findByRoleIdAndFunctionId(role.getId(), function.getId()).isPresent()) {
			throw new DuplicateResourceException("Function is already assigned to this role");
		}

		NexHomeRoleFunction roleFunction = roleFunctionRepository.save(
				roleFunctionMapper.toRoleFunctionEntity(request, role, function, permissionService.currentUserId()));

		auditRecorder.record("GRANT_ADDED", "ROLE", role.getCode(), null, grantValues(roleFunction),
				permissionService.currentUserId());
		log.info("Role function added with Role Function ID {} (Role ID {}, Function ID {})", roleFunction.getId(),
				role.getId(), function.getId());
		return roleFunctionMapper.toRoleFunctionDto(roleFunction);
	}

	@Override
	@Transactional
	public NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request) {
		NexHomeRoleFunction roleFunction = roleFunctionRepository.findById(request.roleFunctionId())
				.orElseThrow(() -> new ResourceNotFoundException("Role function does not exist."));
		requireSuperUserForSuperUserRole(roleFunction.getRole());

		Map<String, Object> before = grantValues(roleFunction);
		roleFunctionMapper.updateRoleFunctionEntity(roleFunction, request, permissionService.currentUserId());
		auditRecorder.record("GRANT_UPDATED", "ROLE", roleFunction.getRole().getCode(), before,
				grantValues(roleFunction), permissionService.currentUserId());

		log.info("Role function updated with Role Function ID {}", roleFunction.getId());
		return roleFunctionMapper.toRoleFunctionDto(roleFunction);
	}

	@Override
	@Transactional
	public void deleteRoleFunction(Long roleFunctionId) {
		NexHomeRoleFunction roleFunction = roleFunctionRepository.findById(roleFunctionId)
				.orElseThrow(() -> new ResourceNotFoundException("Role function does not exist."));
		requireSuperUserForSuperUserRole(roleFunction.getRole());
		auditRecorder.record("GRANT_REMOVED", "ROLE", roleFunction.getRole().getCode(), grantValues(roleFunction),
				null, permissionService.currentUserId());
		roleFunctionRepository.deleteById(roleFunctionId);

		log.info("Role function deleted with Role Function ID {}", roleFunctionId);
	}

	private static Map<String, Object> roleValues(NexHomeRole role) {
		return NexHomeAuditRecorder.values("code", role.getCode(), "name", role.getName(), "activeFlag", role.isActiveFlag());
	}

	private static Map<String, Object> grantValues(NexHomeRoleFunction grant) {
		return NexHomeAuditRecorder.values("function", grant.getFunction().getFunctionCode(), "canRead", grant.isCanRead(),
				"canWrite", grant.isCanWrite(), "canDelete", grant.isCanDelete(), "activeFlag", grant.isActiveFlag());
	}

	/**
	 * The SUPER_USER role is changed (and given or stripped of grants) by super users only, so that ROLE_MANAGE write
	 * cannot deactivate it and lock every super user out.
	 */
	private void requireSuperUserForSuperUserRole(NexHomeRole role) {
		if (NexHomeRole.SUPER_USER.equals(role.getCode())
				&& !permissionService.isSuperUser(permissionService.currentUserId())) {
			throw new AccessDeniedException("Only a super user can change the SUPER_USER role.");
		}
	}
}
