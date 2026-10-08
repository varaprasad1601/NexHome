package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.model.NexHomeFunction;

@Component
public class NexHomeFunctionMapper {

	public NexHomeFunction toFunctionEntity(NexHomeFunctionCreateRequest request, Long actingUserId) {
		NexHomeFunction function = new NexHomeFunction();
		function.setFunctionModule(trimToNull(request.functionModule()));
		function.setFunctionCode(trimToNull(request.functionCode()));
		function.setFunctionName(trimToNull(request.functionName()));
		function.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		function.setCreatedBy(actingUserId);
		function.setLastUpdateBy(actingUserId);
		return function;
	}

	/**
	 * Applies an update of the name and active flag; each keeps its current value when null. Module and code are
	 * never updated.
	 */
	public void updateFunctionEntity(NexHomeFunction function, NexHomeFunctionUpdateRequest request,
			Long actingUserId) {
		if (request.functionName() != null) {
			function.setFunctionName(trimToNull(request.functionName()));
		}
		if (request.activeFlag() != null) {
			function.setActiveFlag(request.activeFlag());
		}
		function.setLastUpdateBy(actingUserId);
	}

	public NexHomeFunctionResponse toFunctionDto(NexHomeFunction function) {
		return new NexHomeFunctionResponse(
				function.getFunctionId(),
				function.getFunctionModule(),
				function.getFunctionCode(),
				function.getFunctionName(),
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
