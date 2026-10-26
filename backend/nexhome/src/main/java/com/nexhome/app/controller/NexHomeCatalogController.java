package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
import com.nexhome.app.service.NexHomeCatalogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The service catalogue. The GET endpoints are public (see {@code NexHomeSecurityConfig}) and show active entries
 * without commission settings; with CATALOG_MANAGE read they show everything. Changes need CATALOG_MANAGE.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class NexHomeCatalogController {

	private final NexHomeCatalogService catalogService;

	/** Categories in display order; {@code includeInactive=true} needs CATALOG_MANAGE read. */
	@GetMapping("/categories")
	public List<NexHomeServiceCategoryResponse> getCategories(
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return catalogService.getCategories(includeInactive);
	}

	@PostMapping("/categories/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceCategoryResponse createCategory(
			@Valid @RequestBody NexHomeServiceCategoryCreateRequest request) {
		return catalogService.createCategory(request);
	}

	@PutMapping("/categories/update")
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceCategoryResponse updateCategory(
			@Valid @RequestBody NexHomeServiceCategoryUpdateRequest request) {
		return catalogService.updateCategory(request);
	}

	@DeleteMapping("/categories/delete/{serviceCategoryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'DELETE')")
	public void deleteCategory(@PathVariable Long serviceCategoryId) {
		catalogService.deleteCategory(serviceCategoryId);
	}

	/**
	 * Services by name. Query parameters (all optional): {@code page} (0-based), {@code size} (1-100, default 10),
	 * {@code categoryId}, {@code pricingType}, {@code search} (part of the name or public id), {@code includeInactive}
	 * (CATALOG_MANAGE read).
	 */
	@GetMapping("/services")
	public NexHomePageResponse<NexHomeServiceResponse> getServices(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String pricingType, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return catalogService.getServices(page, size, categoryId, pricingType, search, includeInactive);
	}

	/**
	 * Prices a service: query parameters {@code serviceId} (required), {@code serviceVariantId}, {@code minutes}
	 * (HOURLY) and {@code quantity} (PER_UNIT).
	 */
	@GetMapping("/services/quote")
	public NexHomeServiceQuoteResponse quote(@RequestParam Long serviceId,
			@RequestParam(required = false) Long serviceVariantId, @RequestParam(required = false) Integer minutes,
			@RequestParam(required = false) Integer quantity) {
		return catalogService.quote(serviceId, serviceVariantId, minutes, quantity);
	}

	@GetMapping("/services/slug/{slug}")
	public NexHomeServiceDetailResponse getServiceBySlug(@PathVariable String slug) {
		return catalogService.getServiceBySlug(slug);
	}

	@GetMapping("/services/{serviceId}")
	public NexHomeServiceDetailResponse getService(@PathVariable Long serviceId) {
		return catalogService.getService(serviceId);
	}

	@PostMapping("/services/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceResponse createService(@Valid @RequestBody NexHomeServiceCreateRequest request) {
		return catalogService.createService(request);
	}

	@PutMapping("/services/update")
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceResponse updateService(@Valid @RequestBody NexHomeServiceUpdateRequest request) {
		return catalogService.updateService(request);
	}

	@DeleteMapping("/services/delete/{serviceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'DELETE')")
	public void deleteService(@PathVariable Long serviceId) {
		catalogService.deleteService(serviceId);
	}

	@PostMapping("/variants/add")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceVariantResponse addVariant(@Valid @RequestBody NexHomeServiceVariantCreateRequest request) {
		return catalogService.addVariant(request);
	}

	@PutMapping("/variants/update")
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceVariantResponse updateVariant(
			@Valid @RequestBody NexHomeServiceVariantUpdateRequest request) {
		return catalogService.updateVariant(request);
	}

	@DeleteMapping("/variants/delete/{serviceVariantId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'DELETE')")
	public void deleteVariant(@PathVariable Long serviceVariantId) {
		catalogService.deleteVariant(serviceVariantId);
	}

	@PostMapping("/media/add")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceMediaResponse addMedia(@Valid @RequestBody NexHomeServiceMediaCreateRequest request) {
		return catalogService.addMedia(request);
	}

	@PutMapping("/media/update")
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceMediaResponse updateMedia(@Valid @RequestBody NexHomeServiceMediaUpdateRequest request) {
		return catalogService.updateMedia(request);
	}

	@DeleteMapping("/media/delete/{serviceMediaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'DELETE')")
	public void deleteMedia(@PathVariable Long serviceMediaId) {
		catalogService.deleteMedia(serviceMediaId);
	}

	@PostMapping("/requirements/add")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceRequirementResponse addRequirement(
			@Valid @RequestBody NexHomeServiceRequirementCreateRequest request) {
		return catalogService.addRequirement(request);
	}

	@PutMapping("/requirements/update")
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'WRITE')")
	public NexHomeServiceRequirementResponse updateRequirement(
			@Valid @RequestBody NexHomeServiceRequirementUpdateRequest request) {
		return catalogService.updateRequirement(request);
	}

	@DeleteMapping("/requirements/delete/{serviceRequirementId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('CATALOG_MANAGE', 'DELETE')")
	public void deleteRequirement(@PathVariable Long serviceRequirementId) {
		catalogService.deleteRequirement(serviceRequirementId);
	}
}
