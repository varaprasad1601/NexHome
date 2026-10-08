package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.service.NexHomeFunctionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
public class NexHomeFunctionController {

	private final NexHomeFunctionService functionService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeFunctionResponse createFunction(@Valid @RequestBody NexHomeFunctionCreateRequest request) {
		return functionService.createFunction(request);
	}

	@PutMapping("/update")
	public NexHomeFunctionResponse updateFunction(@Valid @RequestBody NexHomeFunctionUpdateRequest request) {
		return functionService.updateFunction(request);
	}

	/** Also deletes every role function that grants this function. */
	@DeleteMapping("/delete/{functionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteFunction(@PathVariable Long functionId) {
		functionService.deleteFunction(functionId);
	}
}
