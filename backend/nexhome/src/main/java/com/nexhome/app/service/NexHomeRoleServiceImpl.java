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
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeRoleServiceImpl implements NexHomeRoleService {

	// TODO: replace with the authenticated user's id once the login service is implemented
	private static final Long DEFAULT_LOGGED_IN_USER_ID = 1L;

	private final NexHomeRoleRepository roleRepository;
	private final NexHomeFunctionRepository functionRepository;
	private final NexHomeRoleFunctionRepository roleFunctionRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserDetailsRepository userDetailsRepository;
	private final NexHomeUserAddressRepository userAddressRepository;
	private final NexHomeRoleMapper roleMapper;
	private final NexHomeRoleFunctionMapper roleFunctionMapper;

	@Override
	@Transactional
	public NexHomeRoleResponse createRole(NexHomeRoleCreateRequest request) {
		if (roleRepository.findByRoleName(request.roleName().trim()).isPresent()) {
			throw new DuplicateResourceException("Role name already exists");
		}

		NexHomeRole role = roleRepository.save(roleMapper.toRoleEntity(request, DEFAULT_LOGGED_IN_USER_ID));

		log.info("Role created with Role ID {} ({})", role.getRoleId(), role.getRoleName());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional
	public NexHomeRoleResponse updateRole(NexHomeRoleUpdateRequest request) {
		NexHomeRole role = roleRepository.findByRoleId(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));

		// The role name is never updated (the request rejects it, and the column is not updatable)
		roleMapper.updateRoleEntity(role, request, DEFAULT_LOGGED_IN_USER_ID);

		log.info("Role updated with Role ID {}", role.getRoleId());
		return roleMapper.toRoleDto(role);
	}

	@Override
	@Transactional
	public void deleteRole(Long roleId) {
		if (roleRepository.findByRoleId(roleId).isEmpty()) {
			throw new ResourceNotFoundException("Role does not exist.");
		}

		// Children first, so no foreign key is violated: users' addresses and details, the users, role functions
		int addresses = userAddressRepository.deleteAllByRoleId(roleId);
		int details = userDetailsRepository.deleteAllByRoleId(roleId);
		int users = userRepository.deleteAllByRoleId(roleId);
		int roleFunctions = roleFunctionRepository.deleteAllByRoleId(roleId);
		roleRepository.deleteById(roleId);

		log.info("Role deleted with Role ID {} ({} role functions, {} users, {} user details, {} addresses)", roleId,
				roleFunctions, users, details, addresses);
	}

	@Override
	@Transactional
	public NexHomeRoleFunctionResponse addRoleFunction(NexHomeRoleFunctionCreateRequest request) {
		NexHomeRole role = roleRepository.findByRoleId(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role does not exist."));
		NexHomeFunction function = functionRepository.findByFunctionId(request.functionId())
				.orElseThrow(() -> new ResourceNotFoundException("Function does not exist."));

		// (role_id, function_id) is unique in nexhome_role_functions
		if (roleFunctionRepository.findByRoleRoleIdAndFunctionFunctionId(role.getRoleId(), function.getFunctionId())
				.isPresent()) {
			throw new DuplicateResourceException("Function is already assigned to this role");
		}

		NexHomeRoleFunction roleFunction = roleFunctionRepository.save(
				roleFunctionMapper.toRoleFunctionEntity(request, role, function, DEFAULT_LOGGED_IN_USER_ID));

		log.info("Role function added with Role Function ID {} (Role ID {}, Function ID {})",
				roleFunction.getRoleFunctionId(), role.getRoleId(), function.getFunctionId());
		return roleFunctionMapper.toRoleFunctionDto(roleFunction);
	}

	@Override
	@Transactional
	public NexHomeRoleFunctionResponse updateRoleFunction(NexHomeRoleFunctionUpdateRequest request) {
		NexHomeRoleFunction roleFunction = roleFunctionRepository.findById(request.roleFunctionId())
				.orElseThrow(() -> new ResourceNotFoundException("Role function does not exist."));

		roleFunctionMapper.updateRoleFunctionEntity(roleFunction, request, DEFAULT_LOGGED_IN_USER_ID);

		log.info("Role function updated with Role Function ID {}", roleFunction.getRoleFunctionId());
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
