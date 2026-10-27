package com.nexhome.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeFunctionMapper;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeFunctionServiceImpl implements NexHomeFunctionService {

	private final NexHomePermissionService permissionService;
	private final NexHomeFunctionRepository functionRepository;
	private final NexHomeRoleFunctionRepository roleFunctionRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomeFunctionMapper functionMapper;

	@Override
	@Transactional
	public NexHomeFunctionResponse createFunction(NexHomeFunctionCreateRequest request) {
		if (functionRepository.findByFunctionCode(request.functionCode().trim()).isPresent()) {
			throw new DuplicateResourceException("Function code already exists");
		}
		String functionType = lookupService.validateCode(Types.FUNCTION_TYPE, request.functionType(),
				"Function type");

		NexHomeFunction function = functionRepository.save(
				functionMapper.toFunctionEntity(request, functionType, permissionService.currentUserId()));

		log.info("Function created with Function ID {} ({})", function.getId(), function.getFunctionCode());
		return functionMapper.toFunctionDto(function);
	}

	@Override
	@Transactional
	public NexHomeFunctionResponse updateFunction(NexHomeFunctionUpdateRequest request) {
		NexHomeFunction function = functionRepository.findById(request.functionId())
				.orElseThrow(() -> new ResourceNotFoundException("Function does not exist."));

		String functionType = request.functionType() == null ? null
				: lookupService.validateCode(Types.FUNCTION_TYPE, request.functionType(), "Function type");
		// Module and code are never updated (the request rejects them, and the columns are not updatable)
		functionMapper.updateFunctionEntity(function, request, functionType, permissionService.currentUserId());

		log.info("Function updated with Function ID {}", function.getId());
		return functionMapper.toFunctionDto(function);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeFunctionResponse> getFunctions() {
		return functionRepository.findAllByOrderByFunctionModuleAscFunctionCodeAsc().stream()
				.map(functionMapper::toFunctionDto)
				.toList();
	}

	@Override
	@Transactional
	public void deleteFunction(Long functionId) {
		if (!functionRepository.existsById(functionId)) {
			throw new ResourceNotFoundException("Function does not exist.");
		}
		// Role functions reference the function with ON DELETE RESTRICT: deactivate a function in use
		if (roleFunctionRepository.existsByFunctionId(functionId)) {
			throw new DuplicateResourceException("Function is assigned to roles; deactivate it instead.");
		}
		functionRepository.deleteById(functionId);

		log.info("Function deleted with Function ID {}", functionId);
	}
}
