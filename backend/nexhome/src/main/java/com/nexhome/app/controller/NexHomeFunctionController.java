package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
	@PreAuthorize("@permissions.can('FUNCTION_MANAGE', 'WRITE')")
	public NexHomeFunctionResponse createFunction(@Valid @RequestBody NexHomeFunctionCreateRequest request) {
		return functionService.createFunction(request);
	}

	/** Every function, active or not (for the admin console's Functions screen). */
	@GetMapping
	@PreAuthorize("@permissions.can('FUNCTION_MANAGE', 'READ')")
	public List<NexHomeFunctionResponse> getFunctions() {
		return functionService.getFunctions();
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.can('FUNCTION_MANAGE', 'WRITE')")
	public NexHomeFunctionResponse updateFunction(@Valid @RequestBody NexHomeFunctionUpdateRequest request) {
		return functionService.updateFunction(request);
	}

	/** Only a function no role is granted can be deleted (409 otherwise); deactivate a function that is in use. */
	@DeleteMapping("/delete/{functionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('FUNCTION_MANAGE', 'DELETE')")
	public void deleteFunction(@PathVariable Long functionId) {
		functionService.deleteFunction(functionId);
	}
}
