package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupTypeResponse;
import com.nexhome.app.dto.NexHomeLookupTypeUpdateRequest;
import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;

public interface NexHomeLookupService {

	NexHomeLookupTypeResponse createLookupType(NexHomeLookupTypeCreateRequest request);

	NexHomeLookupTypeResponse updateLookupType(NexHomeLookupTypeUpdateRequest request);

	List<NexHomeLookupTypeResponse> getLookupTypes();

	NexHomeLookupValueResponse addLookupValue(NexHomeLookupValueCreateRequest request);

	NexHomeLookupValueResponse updateLookupValue(NexHomeLookupValueUpdateRequest request);

	/** Every value of a type, active or not, for lookup maintenance. */
	List<NexHomeLookupValueResponse> getLookupTypeValues(Long lookupTypeId);

	/** The active values of an active type, in display order: the options of a UI dropdown. */
	List<NexHomeLookupValueResponse> getActiveLookupValues(String typeCode);

	/**
	 * Checks a submitted code against the active values of a lookup type and returns it trimmed.
	 *
	 * @param fieldName how the field is named in the error message, e.g. "Account status"
	 * @throws com.nexhome.app.exception.InvalidRequestException if the code is not an active value of the type
	 */
	String validateCode(String typeCode, String code, String fieldName);
}
