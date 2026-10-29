package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceCategoryCreateRequest;
import com.nexhome.app.dto.NexHomeServiceCategoryResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryUpdateRequest;
import com.nexhome.app.model.NexHomeServiceCategory;

@Component
public class NexHomeServiceCategoryMapper {

	/** The slug is chosen and checked by the service. */
	public NexHomeServiceCategory toServiceCategoryEntity(NexHomeServiceCategoryCreateRequest request, String slug,
			Long actingUserId) {
		NexHomeServiceCategory category = new NexHomeServiceCategory();
		category.setName(trimToNull(request.name()));
		category.setSlug(slug);
		category.setDescription(trimToNull(request.description()));
		category.setIconUrl(trimToNull(request.iconUrl()));
		category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		category.setActive(request.isActive() == null || request.isActive());
		category.setCreatedBy(actingUserId);
		category.setUpdatedBy(actingUserId);
		return category;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty description or icon URL is cleared. The
	 * slug is applied by the service.
	 */
	public void updateServiceCategoryEntity(NexHomeServiceCategory category, NexHomeServiceCategoryUpdateRequest request,
			Long actingUserId) {
		if (request.name() != null) {
			category.setName(trimToNull(request.name()));
		}
		if (request.description() != null) {
			category.setDescription(trimToNull(request.description()));
		}
		if (request.iconUrl() != null) {
			category.setIconUrl(trimToNull(request.iconUrl()));
		}
		if (request.displayOrder() != null) {
			category.setDisplayOrder(request.displayOrder());
		}
		if (request.isActive() != null) {
			category.setActive(request.isActive());
		}
		category.setUpdatedBy(actingUserId);
	}

	public NexHomeServiceCategoryResponse toServiceCategoryDto(NexHomeServiceCategory category) {
		return toServiceCategoryDto(category, null);
	}

	/** With the number of partners whose main service it is (the staff list). */
	public NexHomeServiceCategoryResponse toServiceCategoryDto(NexHomeServiceCategory category, Long partnerCount) {
		return new NexHomeServiceCategoryResponse(
				category.getId(),
				category.getPublicId(),
				category.getName(),
				category.getSlug(),
				category.getDescription(),
				category.getIconUrl(),
				category.getDisplayOrder(),
				category.isActive(),
				category.getCreationDate(),
				partnerCount);
	}
}
