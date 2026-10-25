package com.nexhome.app.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;
import com.nexhome.app.model.NexHomeLookupType;
import com.nexhome.app.model.NexHomeLookupValue;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class NexHomeLookupValueMapper {

	private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	/** The default flag is applied by the service, which first clears the type's current default. */
	public NexHomeLookupValue toLookupValueEntity(NexHomeLookupValueCreateRequest request, NexHomeLookupType type,
			Long actingUserId) {
		NexHomeLookupValue value = new NexHomeLookupValue();
		value.setLookupType(type);
		value.setValueCode(trimToNull(request.valueCode()));
		value.setDisplayName(trimToNull(request.displayName()));
		value.setDescription(trimToNull(request.description()));
		value.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		value.setActiveFlag(request.activeFlag() == null || request.activeFlag());
		value.setMetadata(toJson(request.metadata()));
		value.setCreatedBy(actingUserId);
		value.setUpdatedBy(actingUserId);
		return value;
	}

	/**
	 * Applies an update: a null field keeps its current value and the description is always replaced. The type and
	 * code are never updated; the default and active flags are applied by the service.
	 */
	public void updateLookupValueEntity(NexHomeLookupValue value, NexHomeLookupValueUpdateRequest request,
			Long actingUserId) {
		if (request.displayName() != null) {
			value.setDisplayName(trimToNull(request.displayName()));
		}
		value.setDescription(trimToNull(request.description()));
		if (request.displayOrder() != null) {
			value.setDisplayOrder(request.displayOrder());
		}
		if (request.metadata() != null) {
			value.setMetadata(toJson(request.metadata()));
		}
		value.setUpdatedBy(actingUserId);
	}

	public NexHomeLookupValueResponse toLookupValueDto(NexHomeLookupValue value) {
		NexHomeLookupType type = value.getLookupType();
		return new NexHomeLookupValueResponse(
				value.getId(),
				type.getId(),
				type.getTypeCode(),
				value.getValueCode(),
				value.getDisplayName(),
				value.getDescription(),
				value.getDisplayOrder(),
				value.isActiveFlag(),
				value.isDefaultValue(),
				jsonMapper.readValue(value.getMetadata(), METADATA_TYPE),
				value.getCreationDate());
	}

	private String toJson(Map<String, Object> metadata) {
		return metadata == null ? "{}" : jsonMapper.writeValueAsString(metadata);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
