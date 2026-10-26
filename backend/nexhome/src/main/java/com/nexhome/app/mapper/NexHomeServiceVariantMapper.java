package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceVariantCreateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantResponse;
import com.nexhome.app.dto.NexHomeServiceVariantUpdateRequest;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceVariant;

@Component
public class NexHomeServiceVariantMapper {

	public NexHomeServiceVariant toServiceVariantEntity(NexHomeServiceVariantCreateRequest request,
			NexHomeService service, Long actingUserId) {
		NexHomeServiceVariant variant = new NexHomeServiceVariant();
		variant.setService(service);
		variant.setVariantCode(trimToNull(request.variantCode()));
		variant.setName(trimToNull(request.name()));
		variant.setDescription(trimToNull(request.description()));
		variant.setPriceOverride(request.priceOverride());
		variant.setDurationMinutes(request.durationMinutes());
		variant.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		variant.setActive(request.isActive() == null || request.isActive());
		variant.setCreatedBy(actingUserId);
		variant.setUpdatedBy(actingUserId);
		return variant;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty description is cleared, except the price
	 * override and duration, which are always replaced (null: the service's apply). The service and code never
	 * change.
	 */
	public void updateServiceVariantEntity(NexHomeServiceVariant variant, NexHomeServiceVariantUpdateRequest request,
			Long actingUserId) {
		if (request.name() != null) {
			variant.setName(trimToNull(request.name()));
		}
		if (request.description() != null) {
			variant.setDescription(trimToNull(request.description()));
		}
		variant.setPriceOverride(request.priceOverride());
		variant.setDurationMinutes(request.durationMinutes());
		if (request.displayOrder() != null) {
			variant.setDisplayOrder(request.displayOrder());
		}
		if (request.isActive() != null) {
			variant.setActive(request.isActive());
		}
		variant.setUpdatedBy(actingUserId);
	}

	public NexHomeServiceVariantResponse toServiceVariantDto(NexHomeServiceVariant variant) {
		return new NexHomeServiceVariantResponse(
				variant.getId(),
				variant.getPublicId(),
				variant.getService().getId(),
				variant.getVariantCode(),
				variant.getName(),
				variant.getDescription(),
				variant.getPriceOverride(),
				variant.getDurationMinutes(),
				variant.getDisplayOrder(),
				variant.isActive(),
				variant.getCreationDate());
	}
}
