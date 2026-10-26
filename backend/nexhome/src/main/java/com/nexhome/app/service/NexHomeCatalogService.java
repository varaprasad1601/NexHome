package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryCreateRequest;
import com.nexhome.app.dto.NexHomeServiceCategoryResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceCreateRequest;
import com.nexhome.app.dto.NexHomeServiceDetailResponse;
import com.nexhome.app.dto.NexHomeServiceMediaCreateRequest;
import com.nexhome.app.dto.NexHomeServiceMediaResponse;
import com.nexhome.app.dto.NexHomeServiceMediaUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.dto.NexHomeServiceRequirementCreateRequest;
import com.nexhome.app.dto.NexHomeServiceRequirementResponse;
import com.nexhome.app.dto.NexHomeServiceRequirementUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceResponse;
import com.nexhome.app.dto.NexHomeServiceUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantCreateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantResponse;
import com.nexhome.app.dto.NexHomeServiceVariantUpdateRequest;

/**
 * The service catalogue: categories, services, and each service's variants, media and requirements, plus price
 * quotes. Browsing is public and shows only active entries without commission settings; callers with CATALOG_MANAGE
 * read see everything.
 */
public interface NexHomeCatalogService {

	/**
	 * Categories in display order.
	 *
	 * @param includeInactive also inactive ones; needs CATALOG_MANAGE read
	 */
	List<NexHomeServiceCategoryResponse> getCategories(boolean includeInactive);

	NexHomeServiceCategoryResponse createCategory(NexHomeServiceCategoryCreateRequest request);

	NexHomeServiceCategoryResponse updateCategory(NexHomeServiceCategoryUpdateRequest request);

	/** Only a category nothing refers to; otherwise deactivate it. */
	void deleteCategory(Long serviceCategoryId);

	/**
	 * One page of services by name.
	 *
	 * @param search optional text contained in the name or public id
	 * @param includeInactive also inactive services and services of inactive categories; needs CATALOG_MANAGE read
	 */
	NexHomePageResponse<NexHomeServiceResponse> getServices(Integer page, Integer size, Long categoryId,
			String pricingType, String search, boolean includeInactive);

	/** A service with its variants, media and requirements; 404 for an inactive one unless CATALOG_MANAGE read. */
	NexHomeServiceDetailResponse getService(Long serviceId);

	/** {@link #getService} by slug. */
	NexHomeServiceDetailResponse getServiceBySlug(String slug);

	NexHomeServiceResponse createService(NexHomeServiceCreateRequest request);

	NexHomeServiceResponse updateService(NexHomeServiceUpdateRequest request);

	/** Only a service nothing refers to (no variants, media, requirements, FAQs or partners); otherwise deactivate. */
	void deleteService(Long serviceId);

	NexHomeServiceVariantResponse addVariant(NexHomeServiceVariantCreateRequest request);

	NexHomeServiceVariantResponse updateVariant(NexHomeServiceVariantUpdateRequest request);

	void deleteVariant(Long serviceVariantId);

	NexHomeServiceMediaResponse addMedia(NexHomeServiceMediaCreateRequest request);

	NexHomeServiceMediaResponse updateMedia(NexHomeServiceMediaUpdateRequest request);

	void deleteMedia(Long serviceMediaId);

	NexHomeServiceRequirementResponse addRequirement(NexHomeServiceRequirementCreateRequest request);

	NexHomeServiceRequirementResponse updateRequirement(NexHomeServiceRequirementUpdateRequest request);

	void deleteRequirement(Long serviceRequirementId);

	/**
	 * Prices a service, optionally a variant of it: FIXED and INSPECTION charge the unit price, PER_UNIT the unit
	 * price times {@code quantity} (default 1), HOURLY the hourly unit price for {@code minutes} (default: the
	 * variant's or service's duration) raised to the minimum billable time and rounded up to the billing increment.
	 * The total is never below the minimum charge.
	 */
	NexHomeServiceQuoteResponse quote(Long serviceId, Long serviceVariantId, Integer minutes, Integer quantity);
}
