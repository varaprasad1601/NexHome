package com.nexhome.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeServiceActivityResponse;
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
import com.nexhome.app.dto.NexHomeServiceVariantInput;
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
import com.nexhome.app.model.NexHomeLookupCodes.RequirementFor;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.model.NexHomeServiceImage;
import com.nexhome.app.model.NexHomeServiceMedia;
import com.nexhome.app.model.NexHomeServiceRequirement;
import com.nexhome.app.model.NexHomeServiceVariant;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeFaqRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceImageRepository;
import com.nexhome.app.repository.NexHomeServiceMediaRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeServiceRequirementRepository;
import com.nexhome.app.repository.NexHomeServiceVariantRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeCatalogServiceImpl implements NexHomeCatalogService {

	static final String CATALOG_MANAGE = "CATALOG_MANAGE";
	static final int MAX_IMAGES = 5;
	static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
	static final String IMAGE_PATH = "/api/catalog/services/images/";
	/** The REQUIREMENT_TYPE of a chosen requirement option whose metadata names none. */
	private static final String DEFAULT_REQUIREMENT_TYPE = "OTHER";
	/** One week: no single job is priced for longer. */
	private static final int MAX_MINUTES = 7 * 24 * 60;
	private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
	/** Activity log: updates within this long of the creation are part of creating the record. */
	private static final Duration SAME_CHANGE = Duration.ofSeconds(5);
	private static final int MAX_ACTIVITY = 50;

	private final NexHomeServiceCategoryRepository categoryRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomeServiceVariantRepository variantRepository;
	private final NexHomeServiceMediaRepository mediaRepository;
	private final NexHomeServiceImageRepository imageRepository;
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
	private final NexHomeAdminProfileRepository adminProfileRepository;
	private final NexHomeUserRepository userRepository;

	// ------------------------------------------------------------------------------------------------------------
	// Categories
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeServiceCategoryResponse> getCategories(boolean includeInactive) {
		if (!includeInactive) {
			return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
					.map(categoryMapper::toServiceCategoryDto)
					.toList();
		}
		requireCatalogReader();
		// Staff also see how many partners each category has
		Map<Long, Long> partners = partnerProfileRepository.countByMainServiceCategory().stream()
				.collect(Collectors.toMap(NexHomePartnerProfileRepository.CategoryCount::getCategoryId,
						NexHomePartnerProfileRepository.CategoryCount::getPartners));
		return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
				.map(category -> categoryMapper.toServiceCategoryDto(category,
						partners.getOrDefault(category.getId(), 0L)))
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
			String pricingType, Boolean isActive, String search, boolean includeInactive) {
		boolean catalogReader = permissionService.canIfSignedIn(CATALOG_MANAGE, NexHomeAccess.READ.name());
		if (includeInactive && !catalogReader) {
			throw new AccessDeniedException("Inactive services need CATALOG_MANAGE read.");
		}
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
		String type = pricingType == null || pricingType.isBlank() ? null
				: lookupService.validateCode(Types.PRICING_TYPE, pricingType, "Pricing type");

		Page<NexHomeService> services = serviceRepository.search(categoryId, type, isActive, !includeInactive,
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
		String serviceTarget = lookupService.validateCode(Types.SERVICE_TARGET, request.serviceTarget(),
				"Service for");
		String pricingType = lookupService.validateCode(Types.PRICING_TYPE, request.pricingType(), "Pricing type");
		requirePricingTypeAllowed(serviceTarget, pricingType);
		String commissionType = lookupService.validateCode(Types.COMMISSION_TYPE, request.platformCommissionType(),
				"Platform commission type");
		String slug = slugFor(request.slug(), request.name());
		requireFreeServiceSlug(slug, null);
		List<RequirementOption> customerRequirements = requirementOptions(Types.CUSTOMER_REQUIREMENT,
				request.customerRequirements(), "Customer requirement");
		List<RequirementOption> partnerRequirements = requirementOptions(Types.PARTNER_REQUIREMENT,
				request.partnerRequirements(), "Partner requirement");
		if (request.variants() != null
				&& request.variants().stream().anyMatch(variant -> variant.serviceVariantId() != null)) {
			throw new InvalidRequestException("The variants of a new service cannot have a service variant ID.");
		}
		requireValidVariants(request.variants(), pricingType);

		Long actingUserId = permissionService.currentUserId();
		NexHomeService service = serviceMapper.toServiceEntity(request, category, slug, actingUserId);
		service.setServiceTarget(serviceTarget);
		service.setPricingType(pricingType);
		service.setPlatformCommissionType(commissionType);
		requireConsistentPricing(service);
		service = serviceRepository.save(service);

		syncVariants(service, request.variants(), List.of(), actingUserId);
		syncRequirements(service, RequirementFor.CUSTOMER, customerRequirements, actingUserId);
		syncRequirements(service, RequirementFor.PARTNER, partnerRequirements, actingUserId);

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
		String serviceTarget = request.serviceTarget() == null ? null
				: lookupService.validateCode(Types.SERVICE_TARGET, request.serviceTarget(), "Service for");
		String pricingType = request.pricingType() == null ? null
				: lookupService.validateCode(Types.PRICING_TYPE, request.pricingType(), "Pricing type");
		String finalPricingType = pricingType != null ? pricingType : service.getPricingType();
		if (serviceTarget != null || pricingType != null) {
			requirePricingTypeAllowed(serviceTarget != null ? serviceTarget : service.getServiceTarget(),
					finalPricingType);
		}
		String commissionType = request.platformCommissionType() == null ? null
				: lookupService.validateCode(Types.COMMISSION_TYPE, request.platformCommissionType(),
						"Platform commission type");
		String slug = request.slug() == null ? null : request.slug().trim();
		if (slug != null) {
			requireFreeServiceSlug(slug, service.getId());
		}
		List<RequirementOption> customerRequirements = requirementOptions(Types.CUSTOMER_REQUIREMENT,
				request.customerRequirements(), "Customer requirement");
		List<RequirementOption> partnerRequirements = requirementOptions(Types.PARTNER_REQUIREMENT,
				request.partnerRequirements(), "Partner requirement");
		List<NexHomeServiceVariant> variants = request.variants() == null ? List.of()
				: variantRepository.findByServiceIdOrderByDisplayOrderAscNameAsc(service.getId());
		if (request.variants() != null) {
			Set<Long> ids = variants.stream().map(NexHomeServiceVariant::getId).collect(Collectors.toSet());
			Set<Long> listed = new HashSet<>();
			for (NexHomeServiceVariantInput input : request.variants()) {
				if (input.serviceVariantId() != null
						&& (!ids.contains(input.serviceVariantId()) || !listed.add(input.serviceVariantId()))) {
					throw new InvalidRequestException(
							"Service variant " + input.serviceVariantId() + " is not a variant of this service.");
				}
			}
		}
		requireValidVariants(request.variants(), finalPricingType);

		Long actingUserId = permissionService.currentUserId();
		serviceMapper.updateServiceEntity(service, request, actingUserId);
		if (category != null) {
			service.setCategory(category);
		}
		if (serviceTarget != null) {
			service.setServiceTarget(serviceTarget);
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

		if (request.variants() != null) {
			syncVariants(service, request.variants(), variants, actingUserId);
		}
		syncRequirements(service, RequirementFor.CUSTOMER, customerRequirements, actingUserId);
		syncRequirements(service, RequirementFor.PARTNER, partnerRequirements, actingUserId);

		log.info("Service {} updated", service.getPublicId());
		return serviceMapper.toServiceDto(service, true);
	}

	@Override
	@Transactional
	public void deleteService(Long serviceId) {
		NexHomeService service = findService(serviceId);
		if (variantRepository.existsByServiceId(serviceId) || mediaRepository.existsByServiceId(serviceId)
				|| imageRepository.existsByServiceId(serviceId) || requirementRepository.existsByServiceId(serviceId)
				|| faqRepository.existsByServiceId(serviceId) || partnerServiceRepository.existsByServiceId(serviceId)) {
			throw new DuplicateResourceException(
					"Service has variants, images, media, requirements, FAQs or partners; deactivate it instead.");
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
	public NexHomeServiceImageResponse addImage(Long serviceId, String fileName, byte[] data) {
		// Locked, so two uploads at once cannot both pass the limit
		NexHomeService service = serviceRepository.findLockedById(serviceId)
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist."));
		if (data == null || data.length == 0) {
			throw new InvalidRequestException("Choose an image to upload.");
		}
		if (data.length > MAX_IMAGE_BYTES) {
			throw new InvalidRequestException("Image must be at most 5 MB.");
		}
		String contentType = imageContentType(data);
		if (contentType == null) {
			throw new InvalidRequestException("Image must be a JPEG, PNG or WebP file.");
		}
		long count = imageRepository.countByServiceId(serviceId);
		if (count >= MAX_IMAGES) {
			throw new InvalidRequestException("A service can have at most " + MAX_IMAGES + " images.");
		}

		Long actingUserId = permissionService.currentUserId();
		NexHomeServiceImage image = new NexHomeServiceImage();
		image.setService(service);
		image.setFileName(imageFileName(fileName));
		image.setContentType(contentType);
		image.setFileSize(data.length);
		image.setImageData(data);
		image.setDisplayOrder((int) count);
		image.setCreatedBy(actingUserId);
		image.setUpdatedBy(actingUserId);
		image = imageRepository.save(image);

		log.info("Image {} added to service {}", image.getId(), service.getPublicId());
		return new NexHomeServiceImageResponse(image.getId(), serviceId, image.getFileName(), contentType,
				image.getFileSize(), image.getDisplayOrder(), IMAGE_PATH + image.getId(), image.getCreationDate());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeServiceImageFile getImage(Long serviceImageId) {
		NexHomeServiceImage image = imageRepository.findById(serviceImageId)
				.orElseThrow(() -> new ResourceNotFoundException("Service image does not exist."));
		return new NexHomeServiceImageFile(image.getContentType(), image.getImageData());
	}

	@Override
	@Transactional
	public void deleteImage(Long serviceImageId) {
		NexHomeServiceImage image = imageRepository.findById(serviceImageId)
				.orElseThrow(() -> new ResourceNotFoundException("Service image does not exist."));
		imageRepository.delete(image);
		log.info("Image {} deleted", serviceImageId);
	}

	@Override
	@Transactional
	public List<NexHomeServiceImageResponse> orderImages(NexHomeServiceImageOrderRequest request) {
		NexHomeService service = findService(request.serviceId());
		Set<Long> stored = imageRepository.findByServiceIdOrderByDisplayOrderAscIdAsc(service.getId()).stream()
				.map(NexHomeServiceImageRepository.Info::getId)
				.collect(Collectors.toSet());
		List<Long> order = request.serviceImageIds();
		if (order.size() != stored.size() || !stored.equals(new HashSet<>(order))) {
			throw new InvalidRequestException("List every image of the service exactly once.");
		}
		Long actingUserId = permissionService.currentUserId();
		for (int index = 0; index < order.size(); index++) {
			imageRepository.updateDisplayOrder(order.get(index), index, actingUserId);
		}
		log.info("Images of service {} reordered", service.getPublicId());
		return imagesOf(service.getId());
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
	public NexHomeServiceQuoteResponse quote(Long serviceId, Long serviceVariantId, Integer minutes) {
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

		// A variant with its own price charges that (or its discount); otherwise the service's price applies
		boolean variantPriced = variant != null && variant.getPriceOverride() != null;
		BigDecimal regularPrice = variantPriced ? variant.getPriceOverride() : service.getBasePrice();
		BigDecimal unitPrice = variantPriced ? priceToPay(variant.getPriceOverride(), variant.getDiscountPrice())
				: priceToPay(service.getBasePrice(), service.getDiscountPrice());
		Integer duration = variant != null && variant.getDurationMinutes() != null ? variant.getDurationMinutes()
				: service.getEstimatedDurationMinutes();
		Integer billableMinutes = null;
		BigDecimal subtotal;
		if (PricingType.HOURLY.equals(service.getPricingType())) {
			int requested = minutes != null ? minutes : duration != null ? duration : 0;
			if (requested <= 0) {
				throw new InvalidRequestException("Minutes must be more than 0 to price an hourly service.");
			}
			if (requested > MAX_MINUTES) {
				throw new InvalidRequestException("Minutes must be at most " + MAX_MINUTES + ".");
			}
			BigDecimal hourlyPrice = priceToPay(service.getBasePrice(), service.getDiscountPrice());
			if (variantPriced && variant.getDurationMinutes() != null) {
				// A package: its price covers its hours; longer jobs add the hourly price in billing increments
				int covered = variant.getDurationMinutes();
				int extra = requested <= covered ? 0
						: billableMinutes(requested - covered, service.getBillingIncrementMinutes(),
								service.getBillingIncrementMinutes());
				billableMinutes = covered + extra;
				subtotal = unitPrice.add(perMinutes(hourlyPrice, extra));
			} else {
				billableMinutes = billableMinutes(requested, service.getMinimumBillableMinutes(),
						service.getBillingIncrementMinutes());
				subtotal = perMinutes(unitPrice, billableMinutes);
			}
			duration = billableMinutes;
		} else {
			// FIXED, AREA (the variant is the home size) and INSPECTION charge the price once
			subtotal = unitPrice;
		}
		subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

		return new NexHomeServiceQuoteResponse(service.getId(), variant == null ? null : variant.getId(),
				service.getPricingType(), regularPrice, unitPrice, billableMinutes, subtotal,
				service.getMinimumCharge(), subtotal.max(service.getMinimumCharge()), duration);
	}

	private static BigDecimal priceToPay(BigDecimal price, BigDecimal discountPrice) {
		return discountPrice != null ? discountPrice : price;
	}

	private static BigDecimal perMinutes(BigDecimal hourlyPrice, int minutes) {
		return hourlyPrice.multiply(BigDecimal.valueOf(minutes)).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
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
		if (service.getDiscountPrice() != null && service.getDiscountPrice().compareTo(service.getBasePrice()) > 0) {
			throw new InvalidRequestException("Discount price cannot be more than the base price.");
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
	 * The pricing types a service target allows are listed in its SERVICE_TARGET value's metadata
	 * ({@code "pricingTypes": [...]}); a target without that list allows every pricing type.
	 */
	private void requirePricingTypeAllowed(String serviceTarget, String pricingType) {
		NexHomeLookupValueResponse target = lookupService.getActiveLookupValues(Types.SERVICE_TARGET).stream()
				.filter(value -> value.valueCode().equals(serviceTarget))
				.findFirst()
				.orElse(null);
		if (target == null || !(target.metadata().get("pricingTypes") instanceof List<?> allowed)) {
			return;
		}
		if (!allowed.contains(pricingType)) {
			throw new InvalidRequestException("A service for " + target.displayName() + " can be priced only as: "
					+ allowed.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ".");
		}
	}

	/** Rules of a service request's variant list that need no query. */
	private static void requireValidVariants(List<NexHomeServiceVariantInput> variants, String pricingType) {
		if (variants == null) {
			return;
		}
		for (NexHomeServiceVariantInput variant : variants) {
			if (variant.discountPrice() != null && variant.discountPrice().compareTo(variant.price()) > 0) {
				throw new InvalidRequestException(
						"The discount price of " + variant.name().trim() + " cannot be more than its price.");
			}
			if (PricingType.HOURLY.equals(pricingType) && variant.durationMinutes() == null) {
				throw new InvalidRequestException("Give " + variant.name().trim() + " the hours its price covers.");
			}
		}
	}

	/**
	 * Makes the service's variants match the list: listed ones (by id, already checked to be the service's) are
	 * updated, new ones added with a code made from the name, and the others deleted. Display order is list order.
	 */
	private void syncVariants(NexHomeService service, List<NexHomeServiceVariantInput> inputs,
			List<NexHomeServiceVariant> existing, Long actingUserId) {
		if (inputs == null) {
			return;
		}
		Map<Long, NexHomeServiceVariant> byId = existing.stream()
				.collect(Collectors.toMap(NexHomeServiceVariant::getId, Function.identity()));
		Set<String> codes = existing.stream().map(NexHomeServiceVariant::getVariantCode)
				.collect(Collectors.toCollection(HashSet::new));
		Set<Long> kept = new HashSet<>();
		List<NexHomeServiceVariant> added = new ArrayList<>();
		for (int index = 0; index < inputs.size(); index++) {
			NexHomeServiceVariantInput input = inputs.get(index);
			NexHomeServiceVariant variant;
			if (input.serviceVariantId() != null) {
				variant = byId.get(input.serviceVariantId());
				kept.add(variant.getId());
			} else {
				variant = new NexHomeServiceVariant();
				variant.setService(service);
				variant.setVariantCode(variantCodeFor(input.name(), codes));
				variant.setCreatedBy(actingUserId);
				added.add(variant);
			}
			variant.setName(NexHomeText.trimToNull(input.name()));
			variant.setDescription(NexHomeText.trimToNull(input.description()));
			variant.setPriceOverride(input.price());
			variant.setDiscountPrice(input.discountPrice());
			variant.setDurationMinutes(input.durationMinutes());
			variant.setDisplayOrder(index);
			if (input.isActive() != null) {
				variant.setActive(input.isActive());
			}
			variant.setUpdatedBy(actingUserId);
		}
		variantRepository.deleteAll(existing.stream().filter(variant -> !kept.contains(variant.getId())).toList());
		variantRepository.saveAll(added);
	}

	/**
	 * A variant code from the name, e.g. "Split AC" becomes SPLIT_AC, then SPLIT_AC_2 when taken; a name starting with
	 * a digit gets a V_ prefix ("2 Hours": V_2_HOURS). Adds the code to {@code taken}.
	 */
	static String variantCodeFor(String name, Set<String> taken) {
		String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (base.isEmpty()) {
			base = "VARIANT";
		} else if (!Character.isLetter(base.charAt(0))) {
			base = "V_" + base;
		}
		base = base.substring(0, Math.min(base.length(), 70));
		String code = base;
		for (int suffix = 2; taken.contains(code); suffix++) {
			code = base + "_" + suffix;
		}
		taken.add(code);
		return code;
	}

	/** A chosen requirement option: its value code, display name and the REQUIREMENT_TYPE stored with it. */
	record RequirementOption(String code, String name, String requirementType) {
	}

	/**
	 * Checks chosen requirement codes against the active options of the lookup type, without duplicates and in the
	 * given order; null stays null (nothing to change).
	 */
	private List<RequirementOption> requirementOptions(String typeCode, List<String> codes, String fieldName) {
		if (codes == null) {
			return null;
		}
		Map<String, NexHomeLookupValueResponse> options = lookupService.getActiveLookupValues(typeCode).stream()
				.collect(Collectors.toMap(NexHomeLookupValueResponse::valueCode, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));
		Set<String> chosen = new LinkedHashSet<>();
		for (String code : codes) {
			String trimmed = code.trim();
			if (!options.containsKey(trimmed)) {
				throw new InvalidRequestException(fieldName + " must be one of: "
						+ String.join(", ", options.keySet()) + ".");
			}
			chosen.add(trimmed);
		}
		return chosen.stream().map(code -> {
			NexHomeLookupValueResponse option = options.get(code);
			return new RequirementOption(code, option.displayName(),
					option.metadata().get("requirementType") instanceof String type && !type.isBlank() ? type
							: DEFAULT_REQUIREMENT_TYPE);
		}).toList();
	}

	/**
	 * Makes the service's chosen requirements of one side match the options: kept ones take the list order, new ones
	 * are added (mandatory, described by the option's name) and the others deleted. Free-text requirements stay.
	 */
	private void syncRequirements(NexHomeService service, String requirementFor, List<RequirementOption> options,
			Long actingUserId) {
		if (options == null) {
			return;
		}
		Map<String, NexHomeServiceRequirement> existing = requirementRepository
				.findByServiceIdAndRequirementForAndRequirementCodeIsNotNull(service.getId(), requirementFor).stream()
				.collect(Collectors.toMap(NexHomeServiceRequirement::getRequirementCode, Function.identity()));
		List<NexHomeServiceRequirement> added = new ArrayList<>();
		for (int index = 0; index < options.size(); index++) {
			RequirementOption option = options.get(index);
			NexHomeServiceRequirement requirement = existing.remove(option.code());
			if (requirement == null) {
				requirement = new NexHomeServiceRequirement();
				requirement.setService(service);
				requirement.setRequirementFor(requirementFor);
				requirement.setRequirementCode(option.code());
				requirement.setRequirementType(option.requirementType());
				requirement.setDescription(option.name());
				requirement.setCreatedBy(actingUserId);
				added.add(requirement);
			}
			if (requirement.getDisplayOrder() != index) {
				requirement.setDisplayOrder(index);
				requirement.setUpdatedBy(actingUserId);
			}
		}
		// What is left was not chosen again
		requirementRepository.deleteAll(List.copyOf(existing.values()));
		requirementRepository.saveAll(added);
	}

	/** The image type from the file's first bytes (never the client's content type), or null for anything else. */
	static String imageContentType(byte[] data) {
		if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "image/png";
		}
		if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
			return "image/webp";
		}
		return null;
	}

	private static boolean startsWith(byte[] data, int offset, int... expected) {
		if (data.length < offset + expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if ((data[offset + index] & 0xFF) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	/** The uploaded file's name without any path, at most 255 characters; "image" when there is none. */
	static String imageFileName(String fileName) {
		String name = fileName == null ? "" : fileName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
		if (name.isEmpty()) {
			return "image";
		}
		return name.length() <= 255 ? name : name.substring(name.length() - 255);
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
		List<NexHomeServiceVariant> variants = variantRepository.findByServiceIdOrderByDisplayOrderAscNameAsc(serviceId);
		List<NexHomeServiceRequirement> requirements = requirementRepository
				.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId);
		List<NexHomeServiceImageRepository.Info> images = imageRepository
				.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId);
		return new NexHomeServiceDetailResponse(
				serviceMapper.toServiceDto(service, catalogReader),
				images.stream().map(image -> toImageDto(serviceId, image)).toList(),
				variants.stream()
						.filter(variant -> catalogReader || variant.isActive())
						.map(variantMapper::toServiceVariantDto)
						.toList(),
				mediaRepository.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId).stream()
						.filter(media -> catalogReader || media.isActive())
						.map(mediaMapper::toServiceMediaDto)
						.toList(),
				requirements.stream().map(requirementMapper::toServiceRequirementDto).toList(),
				catalogReader ? activityOf(service, variants, images, requirements) : null);
	}

	/** An entry of the activity log before the user's name is known. */
	private record Activity(String action, String subject, Long userId, Instant at) {
	}

	/**
	 * The service's activity log, newest first, from the audit columns: when the service and each variant were created
	 * and last updated, and when each image and requirement was added (the tables keep no history beyond that).
	 */
	private List<NexHomeServiceActivityResponse> activityOf(NexHomeService service,
			List<NexHomeServiceVariant> variants, List<NexHomeServiceImageRepository.Info> images,
			List<NexHomeServiceRequirement> requirements) {
		List<Activity> entries = new ArrayList<>();
		addCreatedAndUpdated(entries, "Service", service.getCreatedBy(), service.getCreationDate(),
				service.getUpdatedBy(), service.getUpdatedDate());
		variants.forEach(variant -> addCreatedAndUpdated(entries, "Variant " + variant.getName(),
				variant.getCreatedBy(), variant.getCreationDate(), variant.getUpdatedBy(), variant.getUpdatedDate()));
		images.forEach(image -> entries.add(new Activity("ADDED", "Image " + image.getFileName(),
				image.getCreatedBy(), image.getCreationDate())));
		requirements.forEach(requirement -> entries.add(new Activity("ADDED",
				(RequirementFor.PARTNER.equals(requirement.getRequirementFor()) ? "Partner" : "Customer")
						+ " requirement " + requirement.getDescription(),
				requirement.getCreatedBy(), requirement.getCreationDate())));
		List<Activity> newest = entries.stream()
				.filter(entry -> entry.at() != null)
				.sorted(Comparator.comparing(Activity::at).reversed())
				.limit(MAX_ACTIVITY)
				.toList();
		Map<Long, String> names = staffNames(newest.stream().map(Activity::userId).filter(id -> id != null)
				.collect(Collectors.toSet()));
		return newest.stream()
				.map(entry -> new NexHomeServiceActivityResponse(entry.action(), entry.subject(), entry.userId(),
						entry.userId() == null ? null : names.get(entry.userId()), entry.at()))
				.toList();
	}

	private static void addCreatedAndUpdated(List<Activity> entries, String subject, Long createdBy,
			Instant creationDate, Long updatedBy, Instant updatedDate) {
		entries.add(new Activity("CREATED", subject, createdBy, creationDate));
		if (creationDate != null && updatedDate != null && updatedDate.isAfter(creationDate.plus(SAME_CHANGE))) {
			entries.add(new Activity("UPDATED", subject, updatedBy, updatedDate));
		}
	}

	/** Display names of users: the admin profile's name, else the mobile number; user 1 is the system account. */
	private Map<Long, String> staffNames(Set<Long> userIds) {
		Map<Long, String> names = new HashMap<>();
		if (userIds.isEmpty()) {
			return names;
		}
		adminProfileRepository.findByUserIdIn(userIds).forEach(profile -> {
			String name = NexHomeText.trimToNull(String.join(" ",
					profile.getFirstName() == null ? "" : profile.getFirstName(),
					profile.getLastName() == null ? "" : profile.getLastName()));
			if (name != null) {
				names.put(profile.getUser().getId(), name);
			}
		});
		userRepository.findAllById(userIds).forEach(user -> names.putIfAbsent(user.getId(),
				user.getId() == 1L ? "System" : user.getCountryCode() + " " + user.getMobileNumber()));
		return names;
	}

	private static NexHomeServiceImageResponse toImageDto(Long serviceId, NexHomeServiceImageRepository.Info image) {
		return new NexHomeServiceImageResponse(image.getId(), serviceId, image.getFileName(), image.getContentType(),
				image.getFileSize(), image.getDisplayOrder(), IMAGE_PATH + image.getId(), image.getCreationDate());
	}

	/** A service's images in display order, without their bytes. */
	private List<NexHomeServiceImageResponse> imagesOf(Long serviceId) {
		return imageRepository.findByServiceIdOrderByDisplayOrderAscIdAsc(serviceId).stream()
				.map(image -> toImageDto(serviceId, image))
				.toList();
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
