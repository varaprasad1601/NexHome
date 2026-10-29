package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceCreateRequest;
import com.nexhome.app.dto.NexHomeServiceResponse;
import com.nexhome.app.dto.NexHomeServiceUpdateRequest;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;

@Component
public class NexHomeServiceMapper {

	/**
	 * Lookup codes are validated by the service beforehand; the slug is chosen and checked by the service. The minimum
	 * charge and commission default to 0, and both booking types and the service itself to allowed / active.
	 */
	public NexHomeService toServiceEntity(NexHomeServiceCreateRequest request, NexHomeServiceCategory category,
			String slug, Long actingUserId) {
		NexHomeService service = new NexHomeService();
		service.setCategory(category);
		service.setName(trimToNull(request.name()));
		service.setSlug(slug);
		service.setShortDescription(trimToNull(request.shortDescription()));
		service.setDescription(NexHomeRichText.clean(request.description()));
		service.setServiceTarget(trimToNull(request.serviceTarget()));
		service.setPricingType(trimToNull(request.pricingType()));
		service.setBasePrice(request.basePrice());
		service.setDiscountPrice(request.discountPrice());
		service.setMinimumCharge(request.minimumCharge() == null ? BigDecimal.ZERO : request.minimumCharge());
		service.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
		service.setMinimumBillableMinutes(request.minimumBillableMinutes());
		service.setBillingIncrementMinutes(request.billingIncrementMinutes());
		service.setPlatformCommissionType(trimToNull(request.platformCommissionType()));
		service.setPlatformCommissionValue(request.platformCommissionValue() == null ? BigDecimal.ZERO
				: request.platformCommissionValue());
		service.setInstantBookingAllowed(request.isInstantBookingAllowed() == null || request.isInstantBookingAllowed());
		service.setScheduledBookingAllowed(request.isScheduledBookingAllowed() == null
				|| request.isScheduledBookingAllowed());
		service.setActive(request.isActive() == null || request.isActive());
		service.setCreatedBy(actingUserId);
		service.setUpdatedBy(actingUserId);
		return service;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty short description or description is
	 * cleared; the discount price is always replaced (null: no discount). The category and slug are applied by the service.
	 */
	public void updateServiceEntity(NexHomeService service, NexHomeServiceUpdateRequest request, Long actingUserId) {
		if (request.name() != null) {
			service.setName(trimToNull(request.name()));
		}
		if (request.shortDescription() != null) {
			service.setShortDescription(trimToNull(request.shortDescription()));
		}
		if (request.description() != null) {
			service.setDescription(NexHomeRichText.clean(request.description()));
		}
		if (request.pricingType() != null) {
			service.setPricingType(trimToNull(request.pricingType()));
		}
		if (request.serviceTarget() != null) {
			service.setServiceTarget(trimToNull(request.serviceTarget()));
		}
		if (request.basePrice() != null) {
			service.setBasePrice(request.basePrice());
		}
		service.setDiscountPrice(request.discountPrice());
		if (request.minimumCharge() != null) {
			service.setMinimumCharge(request.minimumCharge());
		}
		if (request.estimatedDurationMinutes() != null) {
			service.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
		}
		if (request.minimumBillableMinutes() != null) {
			service.setMinimumBillableMinutes(request.minimumBillableMinutes());
		}
		if (request.billingIncrementMinutes() != null) {
			service.setBillingIncrementMinutes(request.billingIncrementMinutes());
		}
		if (request.platformCommissionType() != null) {
			service.setPlatformCommissionType(trimToNull(request.platformCommissionType()));
		}
		if (request.platformCommissionValue() != null) {
			service.setPlatformCommissionValue(request.platformCommissionValue());
		}
		if (request.isInstantBookingAllowed() != null) {
			service.setInstantBookingAllowed(request.isInstantBookingAllowed());
		}
		if (request.isScheduledBookingAllowed() != null) {
			service.setScheduledBookingAllowed(request.isScheduledBookingAllowed());
		}
		if (request.isActive() != null) {
			service.setActive(request.isActive());
		}
		service.setUpdatedBy(actingUserId);
	}

	/** @param showCommission false hides the platform commission (everyone but CATALOG_MANAGE readers) */
	public NexHomeServiceResponse toServiceDto(NexHomeService service, boolean showCommission) {
		NexHomeServiceCategory category = service.getCategory();
		return new NexHomeServiceResponse(
				service.getId(),
				service.getPublicId(),
				category.getId(),
				category.getName(),
				service.getName(),
				service.getSlug(),
				service.getShortDescription(),
				service.getDescription(),
				service.getServiceTarget(),
				service.getPricingType(),
				service.getBasePrice(),
				service.getDiscountPrice(),
				service.getMinimumCharge(),
				service.getEstimatedDurationMinutes(),
				service.getMinimumBillableMinutes(),
				service.getBillingIncrementMinutes(),
				showCommission ? service.getPlatformCommissionType() : null,
				showCommission ? service.getPlatformCommissionValue() : null,
				service.isInstantBookingAllowed(),
				service.isScheduledBookingAllowed(),
				service.isActive(),
				service.getCreationDate());
	}
}
