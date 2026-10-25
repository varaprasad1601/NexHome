package com.nexhome.app.service;

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

		log.info("Role created with Role ID {} ({})", role.getId(), role.getCode());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional
	public NexHomeRoleResponse updateRole(NexHomeRoleUpdateRequest request) {
		NexHomeRole role = roleRepository.findById(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));

		// The code is never updated (the request rejects it, and the column is not updatable)
		roleMapper.updateRoleEntity(role, request, permissionService.currentUserId());

		log.info("Role updated with Role ID {}", role.getId());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional
	public void deleteRole(Short roleId) {
		if (!roleRepository.existsById(roleId)) {
			throw new ResourceNotFoundException("Role does not exist.");
		}
		// User roles and role functions reference the role with ON DELETE RESTRICT: deactivate a role in use
		if (userRoleRepository.existsByRoleId(roleId) || roleFunctionRepository.existsByRoleId(roleId)) {
			throw new DuplicateResourceException("Role is assigned to users or functions; deactivate it instead.");
		}
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

		// (role_id, function_id) is unique in nexhome_role_functions
		if (roleFunctionRepository.findByRoleIdAndFunctionId(role.getId(), function.getId()).isPresent()) {
			throw new DuplicateResourceException("Function is already assigned to this role");
		}

		NexHomeRoleFunction roleFunction = roleFunctionRepository.save(
				roleFunctionMapper.toRoleFunctionEntity(request, role, function, permissionService.currentUserId()));

		log.info("Role function added with Role Function ID {} (Role ID {}, Function ID {})", roleFunction.getId(),
				role.getId(), function.getId());
		return roleFunctionMapper.toRoleFunctionDto(roleFunction);
	}

	@Override
	@Transactional
	public NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request) {
		NexHomeRoleFunction roleFunction = roleFunctionRepository.findById(request.roleFunctionId())
				.orElseThrow(() -> new ResourceNotFoundException("Role function does not exist."));

		roleFunctionMapper.updateRoleFunctionEntity(roleFunction, request, permissionService.currentUserId());

		log.info("Role function updated with Role Function ID {}", roleFunction.getId());
		return roleFunctionMapper.toRoleFunctionDto(roleFunction);
	}

	@Override
	@Transactional
	public void deleteRoleFunction(Long roleFunctionId) {
		if (!roleFunctionRepository.existsById(roleFunctionId)) {
			throw new ResourceNotFoundException("Role function does not exist.");
		}
		roleFunctionRepository.deleteById(roleFunctionId);

		log.info("Role function deleted with Role Function ID {}", roleFunctionId);
	}
}
