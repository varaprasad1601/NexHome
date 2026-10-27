package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;

public interface NexHomeFunctionService {

	NexHomeFunctionResponse createFunction(NexHomeFunctionCreateRequest request);

	NexHomeFunctionResponse updateFunction(NexHomeFunctionUpdateRequest request);

	/** Every function, active or not, by module and code. */
	List<NexHomeFunctionResponse> getFunctions();

	/** Deletes a function no role is granted. A function in use must be deactivated instead. */
	void deleteFunction(Long functionId);
}
