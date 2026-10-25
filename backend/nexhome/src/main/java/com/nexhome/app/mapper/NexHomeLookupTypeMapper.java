package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupTypeResponse;
import com.nexhome.app.dto.NexHomeLookupTypeUpdateRequest;
import com.nexhome.app.model.NexHomeLookupType;

@Component
public class NexHomeLookupTypeMapper {

	public NexHomeLookupType toLookupTypeEntity(NexHomeLookupTypeCreateRequest request, Long actingUserId) {
		NexHomeLookupType type = new NexHomeLookupType();
		type.setTypeCode(trimToNull(request.typeCode()));
		type.setTypeName(trimToNull(request.typeName()));
		type.setDescription(trimToNull(request.description()));
		type.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		type.setSystemManaged(Boolean.TRUE.equals(request.isSystemManaged()));
		type.setCreatedBy(actingUserId);
		type.setUpdatedBy(actingUserId);
		return type;
	}

	/**
	 * Applies an update: a null name keeps its current value and the description is always replaced. The code and
	 * system-managed flag are never updated; the active flag is applied by the service.
	 */
	public void updateLookupTypeEntity(NexHomeLookupType type, NexHomeLookupTypeUpdateRequest request,
			Long actingUserId) {
		if (request.typeName() != null) {
			type.setTypeName(trimToNull(request.typeName()));
		}
		type.setDescription(trimToNull(request.description()));
		type.setUpdatedBy(actingUserId);
	}

	public NexHomeLookupTypeResponse toLookupTypeDto(NexHomeLookupType type) {
		return new NexHomeLookupTypeResponse(
				type.getId(),
				type.getTypeCode(),
				type.getTypeName(),
				type.getDescription(),
				type.isActiveFlag(),
				type.isSystemManaged(),
				type.getCreationDate());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
