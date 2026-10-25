package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupTypeResponse;
import com.nexhome.app.dto.NexHomeLookupTypeUpdateRequest;
import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;
import com.nexhome.app.service.NexHomeLookupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lookup types and values. There are no deletes: business tables store value codes, so unused types and values are
 * deactivated instead.
 */
@RestController
@RequestMapping("/api/lookups")
@RequiredArgsConstructor
public class NexHomeLookupController {

	private final NexHomeLookupService lookupService;

	@PostMapping("/types/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'WRITE')")
	public NexHomeLookupTypeResponse createLookupType(@Valid @RequestBody NexHomeLookupTypeCreateRequest request) {
		return lookupService.createLookupType(request);
	}

	@PutMapping("/types/update")
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'WRITE')")
	public NexHomeLookupTypeResponse updateLookupType(@Valid @RequestBody NexHomeLookupTypeUpdateRequest request) {
		return lookupService.updateLookupType(request);
	}

	@GetMapping("/types")
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'READ')")
	public List<NexHomeLookupTypeResponse> getLookupTypes() {
		return lookupService.getLookupTypes();
	}

	/** Every value of the type, including inactive ones, for lookup maintenance. */
	@GetMapping("/types/values/{lookupTypeId}")
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'READ')")
	public List<NexHomeLookupValueResponse> getLookupTypeValues(@PathVariable Long lookupTypeId) {
		return lookupService.getLookupTypeValues(lookupTypeId);
	}

	@PostMapping("/values/add")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'WRITE')")
	public NexHomeLookupValueResponse addLookupValue(@Valid @RequestBody NexHomeLookupValueCreateRequest request) {
		return lookupService.addLookupValue(request);
	}

	@PutMapping("/values/update")
	@PreAuthorize("@permissions.can('LOOKUP_MANAGE', 'WRITE')")
	public NexHomeLookupValueResponse updateLookupValue(@Valid @RequestBody NexHomeLookupValueUpdateRequest request) {
		return lookupService.updateLookupValue(request);
	}

	/** Dropdown options: the active values of an active type, e.g. /api/lookups/values/ACCOUNT_STATUS. */
	@GetMapping("/values/{typeCode}")
	public List<NexHomeLookupValueResponse> getActiveLookupValues(@PathVariable String typeCode) {
		return lookupService.getActiveLookupValues(typeCode);
	}
}
