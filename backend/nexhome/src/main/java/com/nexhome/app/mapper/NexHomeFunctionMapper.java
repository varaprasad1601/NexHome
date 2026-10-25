package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.model.NexHomeFunction;

@Component
public class NexHomeFunctionMapper {

	/** The function type must already be validated against the FUNCTION_TYPE lookup. */
	public NexHomeFunction toFunctionEntity(NexHomeFunctionCreateRequest request, String functionType,
			Long actingUserId) {
		NexHomeFunction function = new NexHomeFunction();
		function.setFunctionModule(trimToNull(request.functionModule()));
		function.setFunctionCode(trimToNull(request.functionCode()));
		function.setFunctionName(trimToNull(request.functionName()));
		function.setFunctionType(functionType);
		function.setDescription(trimToNull(request.description()));
		function.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		function.setCreatedBy(actingUserId);
		function.setUpdatedBy(actingUserId);
		return function;
	}

	/**
	 * Applies an update: the name, type and active flag keep their current value when null (a non-null type must
	 * already be validated); the description is always replaced. Module and code are never updated.
	 */
	public void updateFunctionEntity(NexHomeFunction function, NexHomeFunctionUpdateRequest request,
			String functionType, Long actingUserId) {
		if (request.functionName() != null) {
			function.setFunctionName(trimToNull(request.functionName()));
		}
		if (functionType != null) {
			function.setFunctionType(functionType);
		}
		function.setDescription(trimToNull(request.description()));
		if (request.activeFlag() != null) {
			function.setActiveFlag(request.activeFlag());
		}
		function.setUpdatedBy(actingUserId);
	}

	public NexHomeFunctionResponse toFunctionDto(NexHomeFunction function) {
		return new NexHomeFunctionResponse(
				function.getId(),
				function.getFunctionModule(),
				function.getFunctionCode(),
				function.getFunctionName(),
				function.getFunctionType(),
				function.getDescription(),
				function.isActiveFlag(),
				function.getCreationDate());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
