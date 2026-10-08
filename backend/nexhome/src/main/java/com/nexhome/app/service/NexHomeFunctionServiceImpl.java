package com.nexhome.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeFunctionMapper;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeFunctionServiceImpl implements NexHomeFunctionService {

	// TODO: replace with the authenticated user's id once the login service is implemented
	private static final Long DEFAULT_LOGGED_IN_USER_ID = 1L;

	private final NexHomeFunctionRepository functionRepository;
	private final NexHomeRoleFunctionRepository roleFunctionRepository;
	private final NexHomeFunctionMapper functionMapper;

	@Override
	@Transactional
	public NexHomeFunctionResponse createFunction(NexHomeFunctionCreateRequest request) {
		// function_code and function_name are both unique
		if (functionRepository.findByFunctionCode(request.functionCode().trim()).isPresent()) {
			throw new DuplicateResourceException("Function code already exists");
		}
		if (functionRepository.findByFunctionName(request.functionName().trim()).isPresent()) {
			throw new DuplicateResourceException("Function name already exists");
		}

		NexHomeFunction function = functionRepository.save(
				functionMapper.toFunctionEntity(request, DEFAULT_LOGGED_IN_USER_ID));

		log.info("Function created with Function ID {} ({})", function.getFunctionId(), function.getFunctionCode());
		return functionMapper.toFunctionDto(function);
	}

	@Override
	@Transactional
	public NexHomeFunctionResponse updateFunction(NexHomeFunctionUpdateRequest request) {
		NexHomeFunction function = functionRepository.findByFunctionId(request.functionId())
				.orElseThrow(() -> new ResourceNotFoundException("Function does not exist."));

		// Module and code are never updated (the request rejects them, and the columns are not updatable)
		if (request.functionName() != null) {
			String functionName = request.functionName().trim();
			if (!functionName.equals(function.getFunctionName())
					&& functionRepository.findByFunctionName(functionName).isPresent()) {
				throw new DuplicateResourceException("Function name already exists");
			}
		}
		functionMapper.updateFunctionEntity(function, request, DEFAULT_LOGGED_IN_USER_ID);

		log.info("Function updated with Function ID {}", function.getFunctionId());
		return functionMapper.toFunctionDto(function);
	}

	@Override
	@Transactional
	public void deleteFunction(Long functionId) {
		if (functionRepository.findByFunctionId(functionId).isEmpty()) {
			throw new ResourceNotFoundException("Function does not exist.");
		}

		// Role functions reference the function, so they go first
		int roleFunctions = roleFunctionRepository.deleteAllByFunctionId(functionId);
		functionRepository.deleteById(functionId);

		log.info("Function deleted with Function ID {} ({} role functions)", functionId, roleFunctions);
	}
}
