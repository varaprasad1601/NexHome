package com.nexhome.app.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupTypeResponse;
import com.nexhome.app.dto.NexHomeLookupTypeUpdateRequest;
import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeLookupTypeMapper;
import com.nexhome.app.mapper.NexHomeLookupValueMapper;
import com.nexhome.app.model.NexHomeLookupType;
import com.nexhome.app.model.NexHomeLookupValue;
import com.nexhome.app.repository.NexHomeLookupTypeRepository;
import com.nexhome.app.repository.NexHomeLookupValueRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeLookupServiceImpl implements NexHomeLookupService {

	private final NexHomePermissionService permissionService;
	private final NexHomeAuditRecorder auditRecorder;
	private final NexHomeLookupTypeRepository lookupTypeRepository;
	private final NexHomeLookupValueRepository lookupValueRepository;
	private final NexHomeLookupTypeMapper lookupTypeMapper;
	private final NexHomeLookupValueMapper lookupValueMapper;

	@Override
	@Transactional
	public NexHomeLookupTypeResponse createLookupType(NexHomeLookupTypeCreateRequest request) {
		if (lookupTypeRepository.findByTypeCode(request.typeCode().trim()).isPresent()) {
			throw new DuplicateResourceException("Lookup type code already exists");
		}

		NexHomeLookupType type = lookupTypeRepository.save(
				lookupTypeMapper.toLookupTypeEntity(request, permissionService.currentUserId()));

		auditRecorder.record("TYPE_CREATED", "LOOKUP", type.getTypeCode(), null, NexHomeAuditRecorder.values("typeName",
				type.getTypeName(), "activeFlag", type.isActiveFlag()), permissionService.currentUserId());
		log.info("Lookup type created with Lookup Type ID {} ({})", type.getId(), type.getTypeCode());
		return lookupTypeMapper.toLookupTypeDto(type);
	}

	@Override
	@Transactional
	public NexHomeLookupTypeResponse updateLookupType(NexHomeLookupTypeUpdateRequest request) {
		NexHomeLookupType type = lookupTypeRepository.findById(request.lookupTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("Lookup type does not exist."));

		if (request.activeFlag() != null) {
			// Application logic relies on system-managed types, so they always stay active
			if (!request.activeFlag() && type.isSystemManaged()) {
				throw new InvalidRequestException("A system managed lookup type cannot be deactivated.");
			}
			type.setActiveFlag(request.activeFlag());
		}
		// The code and system-managed flag are never updated (the request rejects them, the columns are not updatable)
		lookupTypeMapper.updateLookupTypeEntity(type, request, permissionService.currentUserId());

		auditRecorder.record("TYPE_UPDATED", "LOOKUP", type.getTypeCode(), null, NexHomeAuditRecorder.values("typeName",
				type.getTypeName(), "activeFlag", type.isActiveFlag()), permissionService.currentUserId());
		log.info("Lookup type updated with Lookup Type ID {}", type.getId());
		return lookupTypeMapper.toLookupTypeDto(type);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeLookupTypeResponse> getLookupTypes() {
		return lookupTypeRepository.findAllByOrderByTypeCodeAsc().stream()
				.map(lookupTypeMapper::toLookupTypeDto)
				.toList();
	}

	@Override
	@Transactional
	public NexHomeLookupValueResponse addLookupValue(NexHomeLookupValueCreateRequest request) {
		NexHomeLookupType type = lookupTypeRepository.findById(request.lookupTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("Lookup type does not exist."));

		// (lookup_type_id, value_code) is unique in nexhome_lookup_values
		if (lookupValueRepository.findByLookupTypeIdAndValueCode(type.getId(), request.valueCode().trim())
				.isPresent()) {
			throw new DuplicateResourceException("Value code already exists for this lookup type");
		}

		NexHomeLookupValue value = lookupValueMapper.toLookupValueEntity(request, type, permissionService.currentUserId());
		if (Boolean.TRUE.equals(request.isDefault())) {
			requireActiveForDefault(value);
			clearCurrentDefault(type.getId());
			value.setDefaultValue(true);
		}
		value = lookupValueRepository.save(value);

		auditRecorder.record("VALUE_ADDED", "LOOKUP", type.getTypeCode(), null, valueValues(value),
				permissionService.currentUserId());
		log.info("Lookup value added with Lookup Value ID {} ({} / {})", value.getId(), type.getTypeCode(),
				value.getValueCode());
		return lookupValueMapper.toLookupValueDto(value);
	}

	@Override
	@Transactional
	public NexHomeLookupValueResponse updateLookupValue(NexHomeLookupValueUpdateRequest request) {
		NexHomeLookupValue value = lookupValueRepository.findById(request.lookupValueId())
				.orElseThrow(() -> new ResourceNotFoundException("Lookup value does not exist."));
		NexHomeLookupType type = value.getLookupType();

		Map<String, Object> before = valueValues(value);
		// The type and code are never updated (the request rejects them, and the columns are not updatable)
		lookupValueMapper.updateLookupValueEntity(value, request, permissionService.currentUserId());
		if (request.activeFlag() != null) {
			// Values of system-managed types are used by application logic, so they always stay active
			if (!request.activeFlag() && type.isSystemManaged()) {
				throw new InvalidRequestException("Values of a system managed lookup type cannot be deactivated.");
			}
			value.setActiveFlag(request.activeFlag());
		}

		if (Boolean.TRUE.equals(request.isDefault()) && !value.isDefaultValue()) {
			requireActiveForDefault(value);
			clearCurrentDefault(type.getId());
			value.setDefaultValue(true);
		}
		else if (Boolean.FALSE.equals(request.isDefault())) {
			value.setDefaultValue(false);
		}
		else if (value.isDefaultValue()) {
			requireActiveForDefault(value);
		}

		auditRecorder.record("VALUE_UPDATED", "LOOKUP", type.getTypeCode(), before, valueValues(value),
				permissionService.currentUserId());
		log.info("Lookup value updated with Lookup Value ID {}", value.getId());
		return lookupValueMapper.toLookupValueDto(value);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeLookupValueResponse> getLookupTypeValues(Long lookupTypeId) {
		if (!lookupTypeRepository.existsById(lookupTypeId)) {
			throw new ResourceNotFoundException("Lookup type does not exist.");
		}
		return lookupValueRepository.findByLookupTypeIdOrderByDisplayOrderAscDisplayNameAsc(lookupTypeId).stream()
				.map(lookupValueMapper::toLookupValueDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeLookupValueResponse> getActiveLookupValues(String typeCode) {
		NexHomeLookupType type = lookupTypeRepository.findByTypeCode(typeCode)
				.filter(NexHomeLookupType::isActiveFlag)
				.orElseThrow(() -> new ResourceNotFoundException("Lookup type does not exist or is inactive."));
		return lookupValueRepository
				.findByLookupTypeTypeCodeAndActiveFlagTrueOrderByDisplayOrderAscDisplayNameAsc(type.getTypeCode())
				.stream()
				.map(lookupValueMapper::toLookupValueDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public String validateCode(String typeCode, String code, String fieldName) {
		String trimmed = code == null ? "" : code.trim();
		if (!trimmed.isEmpty() && lookupValueRepository
				.existsByLookupTypeTypeCodeAndLookupTypeActiveFlagTrueAndValueCodeAndActiveFlagTrue(typeCode, trimmed)) {
			return trimmed;
		}
		List<String> allowed = lookupValueRepository
				.findByLookupTypeTypeCodeAndActiveFlagTrueOrderByDisplayOrderAscDisplayNameAsc(typeCode).stream()
				.map(NexHomeLookupValue::getValueCode)
				.toList();
		throw new InvalidRequestException(fieldName + " must be one of: " + String.join(", ", allowed) + ".");
	}

	private static Map<String, Object> valueValues(NexHomeLookupValue value) {
		return NexHomeAuditRecorder.values("valueCode", value.getValueCode(), "displayName", value.getDisplayName(), "displayOrder",
				value.getDisplayOrder(), "activeFlag", value.isActiveFlag(), "isDefault", value.isDefaultValue());
	}

	private static void requireActiveForDefault(NexHomeLookupValue value) {
		if (!value.isActiveFlag()) {
			throw new InvalidRequestException("An inactive lookup value cannot be the default.");
		}
	}

	/**
	 * Only one default per type: the current one is cleared and flushed before the new default is written, because an
	 * IDENTITY insert runs immediately and the one-default index would reject it.
	 */
	private void clearCurrentDefault(Long lookupTypeId) {
		lookupValueRepository.findByLookupTypeIdAndDefaultValueTrue(lookupTypeId).ifPresent(current -> {
			current.setDefaultValue(false);
			current.setUpdatedBy(permissionService.currentUserId());
			lookupValueRepository.saveAndFlush(current);
		});
	}
}
