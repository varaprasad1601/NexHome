package com.nexhome.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeServiceCategoryMapper;
import com.nexhome.app.mapper.NexHomeServiceMapper;
import com.nexhome.app.mapper.NexHomeServiceMediaMapper;
import com.nexhome.app.mapper.NexHomeServiceRequirementMapper;
import com.nexhome.app.mapper.NexHomeServiceVariantMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.CommissionType;
import com.nexhome.app.model.NexHomeLookupCodes.PricingType;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.model.NexHomeServiceMedia;
import com.nexhome.app.model.NexHomeServiceRequirement;
import com.nexhome.app.model.NexHomeServiceVariant;
import com.nexhome.app.repository.NexHomeFaqRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceMediaRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeServiceRequirementRepository;
import com.nexhome.app.repository.NexHomeServiceVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeCatalogServiceImpl implements NexHomeCatalogService {

	static final String CATALOG_MANAGE = "CATALOG_MANAGE";
	private static final int MAX_QUANTITY = 1000;
	/** One week: no single job is priced for longer. */
	private static final int MAX_MINUTES = 7 * 24 * 60;
	private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

	private final NexHomeServiceCategoryRepository categoryRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomeServiceVariantRepository variantRepository;
	private final NexHomeServiceMediaRepository mediaRepository;
	private final NexHomeServiceRequirementRepository requirementRepository;
	private final NexHomeFaqRepository faqRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePartnerServiceRepository partnerServiceRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeServiceCategoryMapper categoryMapper;
	private final NexHomeServiceMapper serviceMapper;
	private final NexHomeServiceVariantMapper variantMapper;
	private final NexHomeServiceMediaMapper mediaMapper;
	private final NexHomeServiceRequirementMapper requirementMapper;

	// ------------------------------------------------------------------------------------------------------------
	// Categories
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeServiceCategoryResponse> getCategories(boolean includeInactive) {
		if (includeInactive) {
			requireCatalogReader();
		}
		return (includeInactive ? categoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
				: categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc()).stream()
				.map(categoryMapper::toServiceCategoryDto)
				.toList();
	}

	@Override
	@Transactional
	public NexHomeServiceCategoryResponse createCategory(NexHomeServiceCategoryCreateRequest request) {
		String slug = slugFor(request.slug(), request.name());
		requireFreeCategorySlug(slug, null);

		NexHomeServiceCategory category = categoryRepository.save(
				categoryMapper.toServiceCategoryEntity(request, slug, permissionService.currentUserId()));

		log.info("Service category {} ({}) created", category.getPublicId(), slug);
		return categoryMapper.toServiceCategoryDto(category);
	}

	@Override
	@Transactional
	public NexHomeServiceCategoryResponse updateCategory(NexHomeServiceCategoryUpdateRequest request) {
		NexHomeServiceCategory category = findCategory(request.serviceCategoryId());
		// Checked before the entity changes: the query would otherwise flush the change into the unique index first
		String slug = request.slug() == null ? null : request.slug().trim();
		if (slug != null) {
			requireFreeCategorySlug(slug, category.getId());
		}

		categoryMapper.updateServiceCategoryEntity(category, request, permissionService.currentUserId());
		if (slug != null) {
			category.setSlug(slug);
		}

		log.info("Service category {} updated", category.getPublicId());
		return categoryMapper.toServiceCategoryDto(category);
	}

	@Override
	@Transactional
	public void deleteCategory(Long serviceCategoryId) {
		NexHomeServiceCategory category = findCategory(serviceCategoryId);
		if (serviceRepository.existsByCategoryId(category.getId()) || faqRepository.existsByCategoryId(category.getId())
				|| partnerProfileRepository.existsByMainServiceCategoryId(category.getId())) {
			throw new DuplicateResourceException(
					"Category is used by services, FAQs or partner profiles; deactivate it instead.");
		}
		categoryRepository.delete(category);
		log.info("Service category {} deleted", category.getPublicId());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Services
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeServiceResponse> getServices(Integer page, Integer size, Long categoryId,
			String pricingType, String search, boolean includeInactive) {
		boolean catalogReader = permissionService.canIfSignedIn(CATALOG_MANAGE, NexHomeAccess.READ.name());
		if (includeInactive && !catalogReader) {
			throw new AccessDeniedException("Inactive services need CATALOG_MANAGE read.");
		}
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
		String type = pricingType == null || pricingType.isBlank() ? null
				: lookupService.validateCode(Types.PRICING_TYPE, pricingType, "Pricing type");

		Page<NexHomeService> services = serviceRepository.search(categoryId, type, !includeInactive,
				NexHomePaging.searchText(search), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, services,
				services.stream().map(service -> serviceMapper.toServiceDto(service, catalogReader)).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeServiceDetailResponse getService(Long serviceId) {
		return toDetail(serviceRepository.findWithCategoryById(serviceId)
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist.")));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeServiceDetailResponse getServiceBySlug(String slug) {
		return toDetail(serviceRepository.findBySlug(slug)
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist.")));
	}

	@Override
	@Transactional
	public NexHomeServiceResponse createService(NexHomeServiceCreateRequest request) {
		NexHomeServiceCategory category = findActiveCategory(request.categoryId());
		String pricingType = lookupService.validateCode(Types.PRICING_TYPE, request.pricingType(), "Pricing type");
		String commissionType = lookupService.validateCode(Types.COMMISSION_TYPE, request.platformCommissionType(),
				"Platform commission type");
		String slug = slugFor(request.slug(), request.name());
		requireFreeServiceSlug(slug, null);

		NexHomeService service = serviceMapper.toServiceEntity(request, category, slug,
				permissionService.currentUserId());
		service.setPricingType(pricingType);
		service.setPlatformCommissionType(commissionType);
		requireConsistentPricing(service);
		service = serviceRepository.save(service);

		log.info("Service {} ({}) created", service.getPublicId(), slug);
		return serviceMapper.toServiceDto(service, true);
	}

	@Override
	@Transactional
	public NexHomeServiceResponse updateService(NexHomeServiceUpdateRequest request) {
		NexHomeService service = serviceRepository.findWithCategoryById(request.serviceId())
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist."));
		// Everything that queries is done before the entity changes, so no half-applied update is flushed
		NexHomeServiceCategory category = request.categoryId() == null ? null
				: findActiveCategory(request.categoryId());
		String pricingType = request.pricingType() == null ? null
				: lookupService.validateCode(Types.PRICING_TYPE, request.pricingType(), "Pricing type");
		String commissionType = request.platformCommissionType() == null ? null
				: lookupService.validateCode(Types.COMMISSION_TYPE, request.platformCommissionType(),
						"Platform commission type");
		String slug = request.slug() == null ? null : request.slug().trim();
		if (slug != null) {
			requireFreeServiceSlug(slug, service.getId());
		}

		serviceMapper.updateServiceEntity(service, request, permissionService.currentUserId());
		if (category != null) {
			service.setCategory(category);
		}
		if (pricingType != null) {
			service.setPricingType(pricingType);
		}
		if (commissionType != null) {
			service.setPlatformCommissionType(commissionType);
		}
		if (slug != null) {
			service.setSlug(slug);
		}
		requireConsistentPricing(service);

		log.info("Service {} updated", service.getPublicId());
		return serviceMapper.toServiceDto(service, true);
	}

	@Override
	@Transactional
	public void deleteService(Long serviceId) {
		NexHomeService service = findService(serviceId);
		if (variantRepository.existsByServiceId(serviceId) || mediaRepository.existsByServiceId(serviceId)
				|| requirementRepository.existsByServiceId(serviceId) || faqRepository.existsByServiceId(serviceId)
				|| partnerServiceRepository.existsByServiceId(serviceId)) {
			throw new DuplicateResourceException(
					"Service has variants, media, requirements, FAQs or partners; deactivate it instead.");
		}
		serviceRepository.delete(service);
		log.info("Service {} deleted", service.getPublicId());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Variants, media and requirements
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeServiceVariantResponse addVariant(NexHomeServiceVariantCreateRequest request) {
		NexHomeService service = findService(request.serviceId());
		String code = request.variantCode().trim();
		if (variantRepository.existsByServiceIdAndVariantCode(service.getId(), code)) {
			throw new DuplicateResourceException("Variant code already exists for this service");
		}
		NexHomeServiceVariant variant = variantRepository.save(
				variantMapper.toServiceVariantEntity(request, service, permissionService.currentUserId()));

		log.info("Variant {} ({}) added to service {}", variant.getPublicId(), code, service.getPublicId());
		return variantMapper.toServiceVariantDto(variant);
	}

	@Override
	@Transactional
	public NexHomeServiceVariantResponse updateVariant(NexHomeServiceVariantUpdateRequest request) {
		NexHomeServiceVariant variant = variantRepository.findById(request.serviceVariantId())
				.orElseThrow(() -> new ResourceNotFoundException("Service variant does not exist."));
		variantMapper.updateServiceVariantEntity(variant, request, permissionService.currentUserId());
		log.info("Variant {} updated", variant.getPublicId());
		return variantMapper.toServiceVariantDto(variant);
	}

	@Override
	@Transactional
	public void deleteVariant(Long serviceVariantId) {
		NexHomeServiceVariant variant = variantRepository.findById(serviceVariantId)
				.orElseThrow(() -> new ResourceNotFoundException("Service variant does not exist."));
		variantRepository.delete(variant);
		log.info("Variant {} deleted", variant.getPublicId());
	}

	@Override
	@Transactional
	public NexHomeServiceMediaResponse addMedia(NexHomeServiceMediaCreateRequest request) {
		NexHomeService service = findService(request.serviceId());
		String mediaType = lookupService.validateCode(Types.MEDIA_TYPE, request.mediaType(), "Media type");
		NexHomeServiceMedia media = mediaMapper.toServiceMediaEntity(request, service,
				permissionService.currentUserId());
		media.setMediaType(mediaType);
		media = mediaRepository.save(media);

		log.info("Media {} added to service {}", media.getId(), service.getPublicId());
		return mediaMapper.toServiceMediaDto(media);
	}

	@Override
	@Transactional
	public NexHomeServiceMediaResponse updateMedia(NexHomeServiceMediaUpdateRequest request) {
		NexHomeServiceMedia media = mediaRepository.findById(request.serviceMediaId())
				.orElseThrow(() -> new ResourceNotFoundException("Service media does not exist."));
		String mediaType = request.mediaType() == null ? null
				: lookupService.validateCode(Types.MEDIA_TYPE, request.mediaType(), "Media type");

		mediaMapper.updateServiceMediaEntity(media, request, permissionService.currentUserId());
		if (mediaType != null) {
			media.setMediaType(mediaType);
		}

		log.info("Media {} updated", media.getId());
		return mediaMapper.toServiceMediaDto(media);
	}

	@Override
	@Transactional
	public void deleteMedia(Long serviceMediaId) {
		NexHomeServiceMedia media = mediaRepository.findById(serviceMediaId)
				.orElseThrow(() -> new ResourceNotFoundException("Service media does not exist."));
		mediaRepository.delete(media);
		log.info("Media {} deleted", serviceMediaId);
	}

	@Override
	@Transactional
	public NexHomeServiceRequirementResponse addRequirement(NexHomeServiceRequirementCreateRequest request) {
		NexHomeService service = findService(request.serviceId());
		String type = lookupService.validateCode(Types.REQUIREMENT_TYPE, request.requirementType(),
				"Requirement type");
		NexHomeServiceRequirement requirement = requirementMapper.toServiceRequirementEntity(request, service,
				permissionService.currentUserId());
		requirement.setRequirementType(type);
		requirement = requirementRepository.save(requirement);

		log.info("Requirement {} added to service {}", requirement.getId(), service.getPublicId());
		return requirementMapper.toServiceRequirementDto(requirement);
	}

	@Override
	@Transactional
	public NexHomeServiceRequirementResponse updateRequirement(NexHomeServiceRequirementUpdateRequest request) {
		NexHomeServiceRequirement requirement = requirementRepository.findById(request.serviceRequirementId())
				.orElseThrow(() -> new ResourceNotFoundException("Service requirement does not exist."));
		String type = request.requirementType() == null ? null
				: lookupService.validateCode(Types.REQUIREMENT_TYPE, request.requirementType(), "Requirement type");

		requirementMapper.updateServiceRequirementEntity(requirement, request, permissionService.currentUserId());
		if (type != null) {
			requirement.setRequirementType(type);
		}

		log.info("Requirement {} updated", requirement.getId());
		return requirementMapper.toServiceRequirementDto(requirement);
	}

	@Override
	@Transactional
	public void deleteRequirement(Long serviceRequirementId) {
		NexHomeServiceRequirement requirement = requirementRepository.findById(serviceRequirementId)
				.orElseThrow(() -> new ResourceNotFoundException("Service requirement does not exist."));
		requirementRepository.delete(requirement);
		log.info("Requirement {} deleted", serviceRequirementId);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Pricing
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public NexHomeServiceQuoteResponse quote(Long serviceId, Long serviceVariantId, Integer minutes,
			Integer quantity) {
		boolean catalogReader = permissionService.canIfSignedIn(CATALOG_MANAGE, NexHomeAccess.READ.name());
		NexHomeService service = serviceRepository.findWithCategoryById(serviceId)
				.filter(found -> catalogReader || isVisible(found))
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist."));
		NexHomeServiceVariant variant = null;
		if (serviceVariantId != null) {
			variant = variantRepository.findById(serviceVariantId)
					.orElseThrow(() -> new ResourceNotFoundException("Service variant does not exist."));
			if (!variant.getService().getId().equals(service.getId())) {
				throw new InvalidRequestException("The variant does not belong to this service.");
			}
			if (!variant.isActive() && !catalogReader) {
				throw new InvalidRequestException("The variant is not available.");
			}
		}

		BigDecimal unitPrice = variant != null && variant.getPriceOverride() != null ? variant.getPriceOverride()
				: service.getBasePrice();
		Integer duration = variant != null && variant.getDurationMinutes() != null ? variant.getDurationMinutes()
				: service.getEstimatedDurationMinutes();
		Integer billableMinutes = null;
		Integer units = null;
		BigDecimal subtotal;
		switch (service.getPricingType()) {
			case PricingType.HOURLY -> {
				int requested = minutes != null ? minutes : duration != null ? duration : 0;
				if (requested <= 0) {
					throw new InvalidRequestException("Minutes must be more than 0 to price an hourly service.");
				}
				if (requested > MAX_MINUTES) {
					throw new InvalidRequestException("Minutes must be at most " + MAX_MINUTES + ".");
				}
				billableMinutes = billableMinutes(requested, service.getMinimumBillableMinutes(),
						service.getBillingIncrementMinutes());
				subtotal = unitPrice.multiply(BigDecimal.valueOf(billableMinutes))
						.divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
				duration = billableMinutes;
			}
			case PricingType.PER_UNIT -> {
				units = quantity == null ? 1 : quantity;
				if (units < 1 || units > MAX_QUANTITY) {
					throw new InvalidRequestException("Quantity must be between 1 and " + MAX_QUANTITY + ".");
				}
				subtotal = unitPrice.multiply(BigDecimal.valueOf(units));
			}
			// FIXED and INSPECTION (and any pricing type added later) charge the unit price once
			default -> subtotal = unitPrice;
		}
		subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

		return new NexHomeServiceQuoteResponse(service.getId(), variant == null ? null : variant.getId(),
				service.getPricingType(), unitPrice, units, billableMinutes, subtotal, service.getMinimumCharge(),
				subtotal.max(service.getMinimumCharge()), duration);
	}

	/**
	 * Hourly billing: at least the minimum, then whole increments, e.g. minimum 60 and increment 30 bill 70 requested
	 * minutes as 90.
	 */
	static int billableMinutes(int requested, int minimum, int increment) {
		if (requested <= minimum) {
			return minimum;
		}
		// In long, so huge inputs cannot wrap around; quote() caps the minutes anyway
		long extra = (long) requested - minimum;
		long billable = minimum + ((extra + increment - 1) / increment) * increment;
		return (int) Math.min(billable, Integer.MAX_VALUE);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Rules
	// ------------------------------------------------------------------------------------------------------------

	/** The same rules as the table's check constraints, with readable messages. */
	private static void requireConsistentPricing(NexHomeService service) {
		if (PricingType.HOURLY.equals(service.getPricingType())
				&& (service.getMinimumBillableMinutes() == null || service.getBillingIncrementMinutes() == null)) {
			throw new InvalidRequestException(
					"Hourly pricing needs the minimum billable minutes and the billing increment.");
		}
		if (CommissionType.PERCENTAGE.equals(service.getPlatformCommissionType())
				&& service.getPlatformCommissionValue().compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new InvalidRequestException("A percentage commission cannot be more than 100.");
		}
		if (!service.isInstantBookingAllowed() && !service.isScheduledBookingAllowed()) {
			throw new InvalidRequestException("At least one of instant and scheduled booking must be allowed.");
		}
	}

	/**
	 * Public callers see only active services of active categories, and only their active variants and media; staff
	 * with CATALOG_MANAGE read see everything, including the commission.
	 */
	private NexHomeServiceDetailResponse toDetail(NexHomeService service) {
		boolean catalogReader = permissionService.canIfSignedIn(CATALOG_MANAGE, NexHomeAccess.READ.name());
		if (!catalogReader && !isVisible(service)) {
			throw new ResourceNotFoundException("Service does not exist.");
		}
		Long serviceId = service.getId();
		return new NexHomeServiceDetailResponse(
				serviceMapper.toServiceDto(service, catalogReader),
				variantRepository.findByServiceIdOrderByDisplayOrderAscNameAsc(serviceId).stream()
						.filter(variant -> catalogReader || variant.isActive())
						.map(variantMapper::toServiceVariantDto)
						.toList(),
				mediaRepository.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId).stream()
						.filter(media -> catalogReader || media.isActive())
						.map(mediaMapper::toServiceMediaDto)
						.toList(),
				requirementRepository.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId).stream()
						.map(requirementMapper::toServiceRequirementDto)
						.toList());
	}

	private static boolean isVisible(NexHomeService service) {
		return service.isActive() && service.getCategory().isActive();
	}

	/** The requested slug, or one made from the name. */
	private static String slugFor(String requested, String name) {
		String slug = requested == null || requested.isBlank() ? NexHomeText.slugOf(name) : requested.trim();
		if (slug.isEmpty()) {
			throw new InvalidRequestException("Slug is required when the name has no letters a-z or digits.");
		}
		return slug;
	}

	private void requireFreeCategorySlug(String slug, Long categoryId) {
		categoryRepository.findBySlug(slug)
				.filter(other -> !other.getId().equals(categoryId))
				.ifPresent(other -> {
					throw new DuplicateResourceException("Another category has this slug.");
				});
	}

	private void requireFreeServiceSlug(String slug, Long serviceId) {
		serviceRepository.findBySlug(slug)
				.filter(other -> !other.getId().equals(serviceId))
				.ifPresent(other -> {
					throw new DuplicateResourceException("Another service has this slug.");
				});
	}

	private void requireCatalogReader() {
		if (!permissionService.canIfSignedIn(CATALOG_MANAGE, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("Inactive catalogue entries need CATALOG_MANAGE read.");
		}
	}

	private NexHomeServiceCategory findCategory(Long serviceCategoryId) {
		return categoryRepository.findById(serviceCategoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Service category does not exist."));
	}

	private NexHomeServiceCategory findActiveCategory(Long serviceCategoryId) {
		return categoryRepository.findByIdAndActiveTrue(serviceCategoryId)
				.orElseThrow(() -> new ResourceNotFoundException("Service category does not exist or is inactive."));
	}

	private NexHomeService findService(Long serviceId) {
		return serviceRepository.findById(serviceId)
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist."));
	}
}
