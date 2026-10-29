package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomePartnerTimeOffResponse;
import com.nexhome.app.model.NexHomePartnerTimeOff;

/** Time off (Module 5). */
@Component
public class NexHomeAvailabilityMapper {

	public NexHomePartnerTimeOffResponse toTimeOffDto(NexHomePartnerTimeOff timeOff) {
		return new NexHomePartnerTimeOffResponse(
				timeOff.getId(),
				timeOff.getPartner().getId(),
				timeOff.getStartsAt(),
				timeOff.getEndsAt(),
				timeOff.getReason(),
				timeOff.getCancelledAt(),
				timeOff.getCancelledBy(),
				timeOff.getCancellationReason(),
				timeOff.getCreationDate());
	}
}
