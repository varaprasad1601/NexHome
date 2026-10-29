package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryCreateRequest;
import com.nexhome.app.dto.NexHomeServiceCategoryResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceCreateRequest;
import com.nexhome.app.dto.NexHomeServiceDetailResponse;
import com.nexhome.app.dto.NexHomeServiceImageFile;
import com.nexhome.app.dto.NexHomeServiceImageOrderRequest;
import com.nexhome.app.dto.NexHomeServiceImageResponse;
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
	 * @param isActive optional: only active (true) or inactive (false) services
	 * @param search optional text contained in the name or public id
	 * @param includeInactive also inactive services and services of inactive categories; needs CATALOG_MANAGE read
	 */
	NexHomePageResponse<NexHomeServiceResponse> getServices(Integer page, Integer size, Long categoryId,
			String pricingType, Boolean isActive, String search, boolean includeInactive);

	/**
	 * A service with its images, variants, media and requirements; 404 for an inactive one unless CATALOG_MANAGE
	 * read.
	 */
	NexHomeServiceDetailResponse getService(Long serviceId);

	/** {@link #getService} by slug. */
	NexHomeServiceDetailResponse getServiceBySlug(String slug);

	/** Creates a service with its variants and chosen requirements. */
	NexHomeServiceResponse createService(NexHomeServiceCreateRequest request);

	/** Updates a service; variants and chosen requirements are replaced when their lists are sent. */
	NexHomeServiceResponse updateService(NexHomeServiceUpdateRequest request);

	/**
	 * Only a service nothing refers to (no variants, images, media, requirements, FAQs or partners); otherwise
	 * deactivate.
	 */
	void deleteService(Long serviceId);

	NexHomeServiceVariantResponse addVariant(NexHomeServiceVariantCreateRequest request);

	NexHomeServiceVariantResponse updateVariant(NexHomeServiceVariantUpdateRequest request);

	void deleteVariant(Long serviceVariantId);

	NexHomeServiceMediaResponse addMedia(NexHomeServiceMediaCreateRequest request);

	NexHomeServiceMediaResponse updateMedia(NexHomeServiceMediaUpdateRequest request);

	void deleteMedia(Long serviceMediaId);

	/**
	 * Stores an image of a service in the database: JPEG, PNG or WebP by its bytes, at most 5 MB, at most 5 per
	 * service.
	 */
	NexHomeServiceImageResponse addImage(Long serviceId, String fileName, byte[] data);

	/** An image's bytes; public by id. */
	NexHomeServiceImageFile getImage(Long serviceImageId);

	void deleteImage(Long serviceImageId);

	/** Puts the service's images in the given order (every one exactly once); the first is the cover. */
	List<NexHomeServiceImageResponse> orderImages(NexHomeServiceImageOrderRequest request);

	NexHomeServiceRequirementResponse addRequirement(NexHomeServiceRequirementCreateRequest request);

	NexHomeServiceRequirementResponse updateRequirement(NexHomeServiceRequirementUpdateRequest request);

	void deleteRequirement(Long serviceRequirementId);

	/**
	 * Prices a service, optionally a variant of it. The price is the variant's (when it has one), else the service's,
	 * and its discount price when set. FIXED, AREA and INSPECTION charge it once. HOURLY: a variant with hours is a
	 * package covering them, longer jobs adding the service's hourly price in billing increments; without one, the
	 * hourly price for {@code minutes} (default: the variant's or service's duration) raised to the minimum billable
	 * time and rounded up to the billing increment. The total is never below the minimum charge.
	 */
	NexHomeServiceQuoteResponse quote(Long serviceId, Long serviceVariantId, Integer minutes);
}
