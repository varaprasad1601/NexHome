package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceRequirementCreateRequest;
import com.nexhome.app.dto.NexHomeServiceRequirementResponse;
import com.nexhome.app.dto.NexHomeServiceRequirementUpdateRequest;
import com.nexhome.app.model.NexHomeLookupCodes.RequirementFor;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceRequirement;

@Component
public class NexHomeServiceRequirementMapper {

	/** The requirement type is validated by the service beforehand; a requirement is mandatory unless told not and for
	 * the customer unless for the partner. */
	public NexHomeServiceRequirement toServiceRequirementEntity(NexHomeServiceRequirementCreateRequest request,
			NexHomeService service, Long actingUserId) {
		NexHomeServiceRequirement requirement = new NexHomeServiceRequirement();
		requirement.setService(service);
		requirement.setRequirementType(trimToNull(request.requirementType()));
		requirement.setRequirementFor(request.requirementFor() == null ? RequirementFor.CUSTOMER
				: request.requirementFor());
		requirement.setDescription(trimToNull(request.description()));
		requirement.setMandatory(request.isMandatory() == null || request.isMandatory());
		requirement.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		requirement.setCreatedBy(actingUserId);
		requirement.setUpdatedBy(actingUserId);
		return requirement;
	}

	/** Applies an update: a null field keeps its current value. The service never changes. */
	public void updateServiceRequirementEntity(NexHomeServiceRequirement requirement,
			NexHomeServiceRequirementUpdateRequest request, Long actingUserId) {
		if (request.requirementType() != null) {
			requirement.setRequirementType(trimToNull(request.requirementType()));
		}
		if (request.description() != null) {
			requirement.setDescription(trimToNull(request.description()));
		}
		if (request.isMandatory() != null) {
			requirement.setMandatory(request.isMandatory());
		}
		if (request.displayOrder() != null) {
			requirement.setDisplayOrder(request.displayOrder());
		}
		requirement.setUpdatedBy(actingUserId);
	}

	public NexHomeServiceRequirementResponse toServiceRequirementDto(NexHomeServiceRequirement requirement) {
		return new NexHomeServiceRequirementResponse(
				requirement.getId(),
				requirement.getService().getId(),
				requirement.getRequirementFor(),
				requirement.getRequirementCode(),
				requirement.getRequirementType(),
				requirement.getDescription(),
				requirement.isMandatory(),
				requirement.getDisplayOrder(),
				requirement.getCreationDate());
	}
}
