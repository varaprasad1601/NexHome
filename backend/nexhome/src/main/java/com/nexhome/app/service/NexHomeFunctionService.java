package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;

public interface NexHomeFunctionService {

	NexHomeFunctionResponse createFunction(NexHomeFunctionCreateRequest request);

	NexHomeFunctionResponse updateFunction(NexHomeFunctionUpdateRequest request);

	/** Deletes the function together with every role function that grants it. */
	void deleteFunction(Long functionId);
}
