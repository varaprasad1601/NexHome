package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomePartnerServiceCreateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceResponse;
import com.nexhome.app.dto.NexHomePartnerServiceUpdateRequest;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomePartnerService;
import com.nexhome.app.model.NexHomeService;

@Component
public class NexHomePartnerServiceMapper {

	/** A new partner service is active; the primary flag is applied by the service. */
	public NexHomePartnerService toPartnerServiceEntity(NexHomePartnerServiceCreateRequest request,
			NexHomePartnerProfile partner, NexHomeService service, Long actingUserId) {
		NexHomePartnerService partnerService = new NexHomePartnerService();
		partnerService.setPartner(partner);
		partnerService.setService(service);
		partnerService.setExperienceYears(request.experienceYears() == null ? null
				: request.experienceYears().shortValue());
		partnerService.setActive(true);
		partnerService.setCreatedBy(actingUserId);
		partnerService.setUpdatedBy(actingUserId);
		return partnerService;
	}

	/** Applies an update: a null field keeps its current value. The primary flag is applied by the service. */
	public void updatePartnerServiceEntity(NexHomePartnerService partnerService,
			NexHomePartnerServiceUpdateRequest request, Long actingUserId) {
		if (request.experienceYears() != null) {
			partnerService.setExperienceYears(request.experienceYears().shortValue());
		}
		if (request.isActive() != null) {
			partnerService.setActive(request.isActive());
		}
		partnerService.setUpdatedBy(actingUserId);
	}

	public NexHomePartnerServiceResponse toPartnerServiceDto(NexHomePartnerService partnerService) {
		NexHomeService service = partnerService.getService();
		return new NexHomePartnerServiceResponse(
				partnerService.getId(),
				partnerService.getPartner().getId(),
				service.getId(),
				service.getName(),
				partnerService.getExperienceYears() == null ? null : partnerService.getExperienceYears().intValue(),
				partnerService.isPrimaryService(),
				partnerService.isActive(),
				partnerService.getCreationDate());
	}
}
