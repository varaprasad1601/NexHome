package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryCreateRequest;
import com.nexhome.app.dto.NexHomeServiceCategoryUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceCreateRequest;
import com.nexhome.app.dto.NexHomeServiceMediaUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.dto.NexHomeServiceResponse;
import com.nexhome.app.dto.NexHomeServiceUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantCreateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeServiceCategoryMapper;
import com.nexhome.app.mapper.NexHomeServiceMapper;
import com.nexhome.app.mapper.NexHomeServiceMediaMapper;
import com.nexhome.app.mapper.NexHomeServiceRequirementMapper;
import com.nexhome.app.mapper.NexHomeServiceVariantMapper;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.model.NexHomeServiceMedia;
import com.nexhome.app.model.NexHomeServiceVariant;
import com.nexhome.app.repository.NexHomeFaqRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceMediaRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeServiceRequirementRepository;
import com.nexhome.app.repository.NexHomeServiceVariantRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NexHomeCatalogServiceImplTest {

	private static final Long ACTING_USER_ID = 1L;
	private static final Long CATEGORY_ID = 5L;

	private NexHomeServiceCategoryRepository categoryRepository;
	private NexHomeServiceRepository serviceRepository;
	private NexHomeServiceVariantRepository variantRepository;
	private NexHomeServiceMediaRepository mediaRepository;
	private NexHomeServiceRequirementRepository requirementRepository;
	private NexHomeFaqRepository faqRepository;
	private NexHomePartnerProfileRepository partnerProfileRepository;
	private NexHomePartnerServiceRepository partnerServiceRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomeCatalogServiceImpl catalogService;
	private NexHomeServiceCategory category;

	@BeforeEach
	void setUp() {
		categoryRepository = mock(NexHomeServiceCategoryRepository.class);
		serviceRepository = mock(NexHomeServiceRepository.class);
		variantRepository = mock(NexHomeServiceVariantRepository.class);
		mediaRepository = mock(NexHomeServiceMediaRepository.class);
		requirementRepository = mock(NexHomeServiceRequirementRepository.class);
		faqRepository = mock(NexHomeFaqRepository.class);
		partnerProfileRepository = mock(NexHomePartnerProfileRepository.class);
		partnerServiceRepository = mock(NexHomePartnerServiceRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);

		category = category(CATEGORY_ID, "Home Cleaning", "home-cleaning", true);
		when(categoryRepository.findBySlug(any())).thenReturn(Optional.empty());
		when(serviceRepository.findBySlug(any())).thenReturn(Optional.empty());
		when(categoryRepository.save(any())).thenAnswer(invocation -> {
			NexHomeServiceCategory saved = invocation.getArgument(0);
			saved.setId(6L);
			return saved;
		});
		when(serviceRepository.save(any())).thenAnswer(invocation -> {
			NexHomeService saved = invocation.getArgument(0);
			saved.setId(30L);
			return saved;
		});
		when(lookupService.validateCode(any(), any(), any()))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		// The caller is user 1 without CATALOG_MANAGE read unless a test says otherwise
		when(permissionService.currentUserId()).thenReturn(ACTING_USER_ID);
		when(permissionService.canIfSignedIn(any(), any())).thenReturn(false);
		catalogService = new NexHomeCatalogServiceImpl(categoryRepository, serviceRepository, variantRepository,
				mediaRepository, requirementRepository, faqRepository, partnerProfileRepository,
				partnerServiceRepository, lookupService, permissionService, new NexHomeServiceCategoryMapper(),
				new NexHomeServiceMapper(), new NexHomeServiceVariantMapper(), new NexHomeServiceMediaMapper(),
				new NexHomeServiceRequirementMapper());
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Hourly rounding
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void billableMinutesRaiseToTheMinimumThenRoundUpToWholeIncrements() {
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(1, 60, 30)).isEqualTo(60);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(60, 60, 30)).isEqualTo(60);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(61, 60, 30)).isEqualTo(90);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(90, 60, 30)).isEqualTo(90);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(91, 60, 30)).isEqualTo(120);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(61, 60, 1)).isEqualTo(61);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(100, 30, 45)).isEqualTo(120);
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(1440, 60, 60)).isEqualTo(1440);
	}

	@Test
	void billableMinutesDoNotOverflow() {
		assertThat(NexHomeCatalogServiceImpl.billableMinutes(Integer.MAX_VALUE, 60, 30)).isPositive();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Quotes
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void fixedAndInspectionChargeTheUnitPriceOnceIgnoringQuantityAndMinutes() {
		NexHomeService fixed = existingService(30L, "FIXED", "499.5");
		fixed.setEstimatedDurationMinutes(120);
		fixed.setMinimumCharge(new BigDecimal("600"));

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 500, 7);

		assertThat(quote.subtotal()).isEqualByComparingTo("499.50");
		assertThat(quote.subtotal().scale()).isEqualTo(2);
		assertThat(quote.total()).isEqualByComparingTo("600");
		assertThat(quote.quantity()).isNull();
		assertThat(quote.billableMinutes()).isNull();
		assertThat(quote.durationMinutes()).isEqualTo(120);
		assertThat(quote.serviceVariantId()).isNull();

		existingService(31L, "INSPECTION", "199");
		NexHomeServiceQuoteResponse inspection = catalogService.quote(31L, null, null, 3);
		assertThat(inspection.subtotal()).isEqualByComparingTo("199.00");
		assertThat(inspection.total()).isEqualByComparingTo("199.00");
		assertThat(inspection.pricingType()).isEqualTo("INSPECTION");
	}

	@Test
	void unknownPricingTypeIsChargedLikeFixed() {
		existingService(30L, "SUBSCRIPTION", "250");

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 90, 4);

		assertThat(quote.subtotal()).isEqualByComparingTo("250.00");
		assertThat(quote.quantity()).isNull();
		assertThat(quote.billableMinutes()).isNull();
	}

	@Test
	void perUnitMultipliesTheQuantityWithinBounds() {
		existingService(30L, "PER_UNIT", "12.345");

		NexHomeServiceQuoteResponse one = catalogService.quote(30L, null, null, null);
		assertThat(one.quantity()).isEqualTo(1);
		assertThat(one.subtotal()).isEqualByComparingTo("12.35");
		assertThat(catalogService.quote(30L, null, null, 1000).subtotal()).isEqualByComparingTo("12345.00");

		for (int quantity : new int[] { 0, -1, 1001 }) {
			assertThatThrownBy(() -> catalogService.quote(30L, null, null, quantity))
					.isInstanceOf(InvalidRequestException.class)
					.hasMessage("Quantity must be between 1 and 1000.");
		}
	}

	@Test
	void hourlyUsesTheVariantPriceAndDurationWhenNoMinutesAreGiven() {
		NexHomeService hourly = hourlyService(30L, "400");
		hourly.setEstimatedDurationMinutes(120);
		NexHomeServiceVariant variant = existingVariant(40L, hourly, true);
		variant.setPriceOverride(new BigDecimal("600"));
		variant.setDurationMinutes(70);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, null, null);

		assertThat(quote.unitPrice()).isEqualByComparingTo("600");
		assertThat(quote.billableMinutes()).isEqualTo(90);
		assertThat(quote.subtotal()).isEqualByComparingTo("900.00");
		assertThat(quote.durationMinutes()).isEqualTo(90);
		assertThat(quote.serviceVariantId()).isEqualTo(40L);

		// Explicit minutes win over the variant's duration
		assertThat(catalogService.quote(30L, 40L, 150, null).billableMinutes()).isEqualTo(150);
	}

	@Test
	void hourlyRoundsTheSubtotalHalfUpToPaise() {
		NexHomeService hourly = hourlyService(30L, "100");
		hourly.setBillingIncrementMinutes(1);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 61, null);

		assertThat(quote.billableMinutes()).isEqualTo(61);
		assertThat(quote.subtotal()).isEqualByComparingTo("101.67");
	}

	@Test
	void hourlyWithoutAnyDurationNeedsMinutes() {
		hourlyService(30L, "400");

		assertThatThrownBy(() -> catalogService.quote(30L, null, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Minutes must be more than 0 to price an hourly service.");
		assertThatThrownBy(() -> catalogService.quote(30L, null, -30, null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void variantWithoutOverridesUsesTheServicePriceAndDuration() {
		NexHomeService fixed = existingService(30L, "FIXED", "799");
		fixed.setEstimatedDurationMinutes(45);
		existingVariant(40L, fixed, true);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, null, null);

		assertThat(quote.unitPrice()).isEqualByComparingTo("799");
		assertThat(quote.durationMinutes()).isEqualTo(45);
	}

	@Test
	void variantMustBelongToTheServiceBeforeItsStateIsChecked() {
		existingService(30L, "FIXED", "799");
		NexHomeService other = existingService(31L, "FIXED", "999");
		existingVariant(41L, other, false);
		when(variantRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.quote(30L, 41L, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The variant does not belong to this service.");
		assertThatThrownBy(() -> catalogService.quote(30L, 404L, null, null))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service variant does not exist.");
	}

	@Test
	void inactiveVariantCanBeQuotedByCatalogueReaders() {
		NexHomeService fixed = existingService(30L, "FIXED", "799");
		NexHomeServiceVariant variant = existingVariant(40L, fixed, false);
		variant.setPriceOverride(new BigDecimal("1299"));

		assertThatThrownBy(() -> catalogService.quote(30L, 40L, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The variant is not available.");

		catalogReader();
		assertThat(catalogService.quote(30L, 40L, null, null).total()).isEqualByComparingTo("1299.00");
	}

	@Test
	void inactiveServiceOrCategoryIsNotFoundForThePublic() {
		NexHomeService inactive = existingService(30L, "FIXED", "799");
		inactive.setActive(false);
		NexHomeService inInactiveCategory = existingService(31L, "FIXED", "799");
		inInactiveCategory.setCategory(category(9L, "Old", "old", false));
		when(serviceRepository.findWithCategoryById(404L)).thenReturn(Optional.empty());

		for (Long serviceId : List.of(30L, 31L, 404L)) {
			assertThatThrownBy(() -> catalogService.quote(serviceId, null, null, null))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Service does not exist.");
		}

		catalogReader();
		assertThat(catalogService.quote(30L, null, null, null).total()).isEqualByComparingTo("799.00");
		assertThat(catalogService.quote(31L, null, null, null).total()).isEqualByComparingTo("799.00");
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Pricing consistency
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void hourlyServiceNeedsMinimumAndIncrement() {
		activeCategory();

		assertThatThrownBy(() -> catalogService.createService(serviceRequest("HOURLY", null, 30, "FIXED", "0", null,
				null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Hourly pricing needs the minimum billable minutes and the billing increment.");
		assertThatThrownBy(() -> catalogService.createService(serviceRequest("HOURLY", 60, null, "FIXED", "0", null,
				null)))
				.isInstanceOf(InvalidRequestException.class);
		verify(serviceRepository, never()).save(any());

		assertThat(catalogService.createService(serviceRequest(" HOURLY ", 60, 30, "FIXED", "0", null, null))
				.pricingType()).isEqualTo("HOURLY");
	}

	@Test
	void percentageCommissionIsCappedAtHundredButFixedIsNot() {
		activeCategory();

		assertThatThrownBy(() -> catalogService.createService(serviceRequest("FIXED", null, null, "PERCENTAGE",
				"100.01", null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A percentage commission cannot be more than 100.");
		verify(serviceRepository, never()).save(any());

		assertThat(catalogService.createService(serviceRequest("FIXED", null, null, "PERCENTAGE", "100", null, null))
				.platformCommissionValue()).isEqualByComparingTo("100");
		NexHomeServiceResponse fixedCommission = catalogService.createService(serviceRequest("FIXED", null, null,
				" FIXED ", "250", null, null));
		assertThat(fixedCommission.platformCommissionType()).isEqualTo("FIXED");
		assertThat(fixedCommission.platformCommissionValue()).isEqualByComparingTo("250");
	}

	@Test
	void atLeastOneBookingTypeMustBeAllowed() {
		activeCategory();

		assertThatThrownBy(() -> catalogService.createService(serviceRequest("FIXED", null, null, "FIXED", "0",
				false, false)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("At least one of instant and scheduled booking must be allowed.");

		NexHomeServiceResponse scheduledOnly = catalogService.createService(serviceRequest("FIXED", null, null,
				"FIXED", "0", false, null));
		assertThat(scheduledOnly.isInstantBookingAllowed()).isFalse();
		assertThat(scheduledOnly.isScheduledBookingAllowed()).isTrue();
	}

	@Test
	void createdServiceDefaultsAndShowsTheCommission() {
		activeCategory();

		NexHomeServiceResponse response = catalogService.createService(serviceRequest("FIXED", null, null,
				"PERCENTAGE", null, null, null));

		assertThat(response.serviceId()).isEqualTo(30L);
		assertThat(response.categoryId()).isEqualTo(CATEGORY_ID);
		assertThat(response.minimumCharge()).isEqualByComparingTo("0");
		assertThat(response.platformCommissionType()).isEqualTo("PERCENTAGE");
		assertThat(response.platformCommissionValue()).isEqualByComparingTo("0");
		assertThat(response.isActive()).isTrue();
	}

	@Test
	void serviceInMissingOrInactiveCategoryIsRejected() {
		when(categoryRepository.findByIdAndActiveTrue(CATEGORY_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.createService(serviceRequest("FIXED", null, null, "FIXED", "0", null,
				null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service category does not exist or is inactive.");
		verify(serviceRepository, never()).save(any());
	}

	@Test
	void switchingToHourlyUsesTheStoredMinimumAndIncrement() {
		NexHomeService prepared = existingService(30L, "FIXED", "400");
		prepared.setMinimumBillableMinutes(60);
		prepared.setBillingIncrementMinutes(30);
		existingService(31L, "FIXED", "400");

		assertThat(catalogService.updateService(serviceUpdate(30L, null, null, "HOURLY", null, null)).pricingType())
				.isEqualTo("HOURLY");
		assertThatThrownBy(() -> catalogService.updateService(serviceUpdate(31L, null, null, "HOURLY", null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Hourly pricing needs the minimum billable minutes and the billing increment.");
	}

	@Test
	void switchingToPercentageChecksTheStoredCommissionValue() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		service.setPlatformCommissionType("FIXED");
		service.setPlatformCommissionValue(new BigDecimal("250"));

		assertThatThrownBy(() -> catalogService.updateService(serviceUpdate(30L, null, null, null, "PERCENTAGE",
				null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A percentage commission cannot be more than 100.");
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Slugs
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void slugIsMadeFromTheNameWithoutAccentsOrPunctuation() {
		assertThat(catalogService.createCategory(categoryRequest("  Crème Brûlée & Café -- Deluxe!! ", null)).slug())
				.isEqualTo("creme-brulee-cafe-deluxe");
		assertThat(catalogService.createCategory(categoryRequest("Deep Cleaning (2 BHK)", "  ")).slug())
				.isEqualTo("deep-cleaning-2-bhk");
		assertThat(catalogService.createCategory(categoryRequest("Anything", " my-slug ")).slug())
				.isEqualTo("my-slug");
		verify(categoryRepository).findBySlug("creme-brulee-cafe-deluxe");
	}

	@Test
	void nameWithoutLettersOrDigitsNeedsASlug() {
		for (String name : List.of("!!! ***", "पूजा")) {
			assertThatThrownBy(() -> catalogService.createCategory(categoryRequest(name, null)))
					.isInstanceOf(InvalidRequestException.class)
					.hasMessage("Slug is required when the name has no letters a-z or digits.");
		}
		activeCategory();
		assertThatThrownBy(() -> catalogService.createService(new NexHomeServiceCreateRequest(CATEGORY_ID, "---",
				null, null, null, "FIXED", BigDecimal.TEN, null, null, null, null, "FIXED", null, null, null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Slug is required when the name has no letters a-z or digits.");
		verify(categoryRepository, never()).save(any());
		verify(serviceRepository, never()).save(any());

		assertThat(catalogService.createCategory(categoryRequest("पूजा", "pooja")).slug()).isEqualTo("pooja");
	}

	@Test
	void categorySlugMustBeFreeExceptForItsOwnRecord() {
		when(categoryRepository.findBySlug("home-cleaning")).thenReturn(Optional.of(category));
		when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
		NexHomeServiceCategory other = category(7L, "Painting", "painting", true);
		when(categoryRepository.findById(7L)).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> catalogService.createCategory(categoryRequest("Home Cleaning", null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Another category has this slug.");
		verify(categoryRepository, never()).save(any());

		// Its own slug is fine
		assertThat(catalogService.updateCategory(categoryUpdate(CATEGORY_ID, "Cleaning", "home-cleaning")).name())
				.isEqualTo("Cleaning");

		assertThatThrownBy(() -> catalogService.updateCategory(categoryUpdate(7L, "Renamed", " home-cleaning ")))
				.isInstanceOf(DuplicateResourceException.class);
		assertThat(other.getName()).isEqualTo("Painting");
		assertThat(other.getSlug()).isEqualTo("painting");
		assertThat(other.getUpdatedBy()).isNull();
	}

	@Test
	void categorySlugIsCheckedBeforeTheCategoryChanges() {
		when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
		when(categoryRepository.findBySlug("cleaning")).thenAnswer(invocation -> {
			assertThat(category.getName()).isEqualTo("Home Cleaning");
			assertThat(category.getSlug()).isEqualTo("home-cleaning");
			return Optional.empty();
		});

		var response = catalogService.updateCategory(categoryUpdate(CATEGORY_ID, "Cleaning", " cleaning "));

		assertThat(response.slug()).isEqualTo("cleaning");
		assertThat(response.name()).isEqualTo("Cleaning");
		assertThat(category.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void serviceSlugMustBeFreeExceptForItsOwnRecord() {
		activeCategory();
		NexHomeService existing = existingService(30L, "FIXED", "400");
		existing.setSlug("sofa-cleaning");
		NexHomeService other = existingService(31L, "FIXED", "400");
		when(serviceRepository.findBySlug("sofa-cleaning")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> catalogService.createService(new NexHomeServiceCreateRequest(CATEGORY_ID,
				"Sofa Cleaning", null, null, null, "FIXED", BigDecimal.TEN, null, null, null, null, "FIXED", null,
				null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Another service has this slug.");
		verify(serviceRepository, never()).save(any());

		assertThat(catalogService.updateService(serviceUpdate(30L, "Sofa Clean", "sofa-cleaning", null, null, null))
				.name()).isEqualTo("Sofa Clean");
		assertThatThrownBy(() -> catalogService.updateService(serviceUpdate(31L, "Renamed", "sofa-cleaning", null,
				null, null)))
				.isInstanceOf(DuplicateResourceException.class);
		assertThat(other.getName()).isEqualTo("Service 31");
		assertThat(other.getUpdatedBy()).isNull();
	}

	@Test
	void serviceUpdateQueriesBeforeTheServiceChanges() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		NexHomeServiceCategory newCategory = category(8L, "Repairs", "repairs", true);
		when(categoryRepository.findByIdAndActiveTrue(8L)).thenAnswer(invocation -> {
			assertThat(service.getName()).isEqualTo("Service 30");
			return Optional.of(newCategory);
		});
		when(serviceRepository.findBySlug("fan-repair")).thenAnswer(invocation -> {
			assertThat(service.getName()).isEqualTo("Service 30");
			assertThat(service.getCategory()).isSameAs(category);
			return Optional.empty();
		});

		NexHomeServiceResponse response = catalogService.updateService(new NexHomeServiceUpdateRequest(30L, null, 8L,
				"Fan Repair", "fan-repair", null, null, null, null, null, null, null, null, null, null, null, null,
				null));

		assertThat(response.categoryId()).isEqualTo(8L);
		assertThat(response.slug()).isEqualTo("fan-repair");
		assertThat(service.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void unknownLookupCodeOnUpdateLeavesTheServiceUnchanged() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		doThrow(new InvalidRequestException("Pricing type must be one of: FIXED, HOURLY, PER_UNIT, INSPECTION."))
				.when(lookupService).validateCode("PRICING_TYPE", "DAILY", "Pricing type");
		when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.updateService(serviceUpdate(30L, "Renamed", null, "DAILY", null,
				new BigDecimal("1")))).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> catalogService.updateService(new NexHomeServiceUpdateRequest(30L, null, 99L,
				"Renamed", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class);

		assertThat(service.getName()).isEqualTo("Service 30");
		assertThat(service.getPricingType()).isEqualTo("FIXED");
		assertThat(service.getBasePrice()).isEqualByComparingTo("400");
		assertThat(service.getCategory()).isSameAs(category);
		assertThat(service.getUpdatedBy()).isNull();
	}

	@Test
	void unknownMediaTypeOnUpdateLeavesTheMediaUnchanged() {
		NexHomeServiceMedia media = new NexHomeServiceMedia();
		media.setId(50L);
		media.setService(existingService(30L, "FIXED", "400"));
		media.setMediaType("IMAGE");
		media.setStorageKey("old.jpg");
		when(mediaRepository.findById(50L)).thenReturn(Optional.of(media));
		doThrow(new InvalidRequestException("Media type must be one of: IMAGE, VIDEO."))
				.when(lookupService).validateCode("MEDIA_TYPE", "GIF", "Media type");

		assertThatThrownBy(() -> catalogService.updateMedia(new NexHomeServiceMediaUpdateRequest(50L, null, "GIF",
				"new.gif", null, null))).isInstanceOf(InvalidRequestException.class);
		assertThat(media.getStorageKey()).isEqualTo("old.jpg");
		assertThat(media.getMediaType()).isEqualTo("IMAGE");
	}

	@Test
	void variantUpdateAlwaysReplacesPriceOverrideAndDuration() {
		NexHomeServiceVariant variant = existingVariant(40L, existingService(30L, "FIXED", "400"), true);
		variant.setName("2 BHK");
		variant.setPriceOverride(new BigDecimal("1999"));
		variant.setDurationMinutes(240);

		var response = catalogService.updateVariant(new NexHomeServiceVariantUpdateRequest(40L, null, null, null,
				null, null, null, null, null, null));

		assertThat(response.priceOverride()).isNull();
		assertThat(response.durationMinutes()).isNull();
		assertThat(response.name()).isEqualTo("2 BHK");
	}

	@Test
	void variantCodeIsTrimmedBeforeTheDuplicateCheck() {
		existingService(30L, "FIXED", "400");
		when(variantRepository.existsByServiceIdAndVariantCode(30L, "BHK_2")).thenReturn(true);

		assertThatThrownBy(() -> catalogService.addVariant(new NexHomeServiceVariantCreateRequest(30L, " BHK_2 ",
				"2 BHK", null, null, null, null, null)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(variantRepository, never()).save(any());
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Deletes
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void categoryInUseCannotBeDeleted() {
		when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
		List<Consumer<Boolean>> uses = List.of(
				used -> when(serviceRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(used),
				used -> when(faqRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(used),
				used -> when(partnerProfileRepository.existsByMainServiceCategoryId(CATEGORY_ID)).thenReturn(used));

		for (Consumer<Boolean> use : uses) {
			use.accept(true);
			assertThatThrownBy(() -> catalogService.deleteCategory(CATEGORY_ID))
					.isInstanceOf(DuplicateResourceException.class)
					.hasMessage("Category is used by services, FAQs or partner profiles; deactivate it instead.");
			use.accept(false);
		}
		verify(categoryRepository, never()).delete(any(NexHomeServiceCategory.class));

		catalogService.deleteCategory(CATEGORY_ID);
		verify(categoryRepository).delete(category);

		when(categoryRepository.findById(404L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> catalogService.deleteCategory(404L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service category does not exist.");
	}

	@Test
	void serviceWithChildrenOrPartnersCannotBeDeleted() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		List<Consumer<Boolean>> uses = List.of(
				used -> when(variantRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(mediaRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(requirementRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(faqRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(partnerServiceRepository.existsByServiceId(30L)).thenReturn(used));

		for (Consumer<Boolean> use : uses) {
			use.accept(true);
			assertThatThrownBy(() -> catalogService.deleteService(30L))
					.isInstanceOf(DuplicateResourceException.class)
					.hasMessage("Service has variants, media, requirements, FAQs or partners; deactivate it instead.");
			use.accept(false);
		}
		verify(serviceRepository, never()).delete(any(NexHomeService.class));

		catalogService.deleteService(30L);
		verify(serviceRepository).delete(service);
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Lists
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void inactiveCategoriesNeedCatalogueRead() {
		assertThatThrownBy(() -> catalogService.getCategories(true)).isInstanceOf(AccessDeniedException.class);
		verify(categoryRepository, never()).findAllByOrderByDisplayOrderAscNameAsc();

		when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(category));
		assertThat(catalogService.getCategories(false)).extracting("slug").containsExactly("home-cleaning");

		catalogReader();
		NexHomeServiceCategory inactive = category(9L, "Old", "old", false);
		when(categoryRepository.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(category, inactive));
		assertThat(catalogService.getCategories(true)).extracting("isActive").containsExactly(true, false);
	}

	@Test
	void publicServiceListHidesTheCommissionAndInactiveServices() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		when(serviceRepository.search(any(), any(), anyBoolean(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(service)));

		NexHomePageResponse<NexHomeServiceResponse> page = catalogService.getServices(null, null, CATEGORY_ID,
				" FIXED ", "  Sofa ", false);

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).platformCommissionType()).isNull();
		assertThat(page.content().get(0).platformCommissionValue()).isNull();
		assertThat(page.size()).isEqualTo(10);
		verify(serviceRepository).search(eq(CATEGORY_ID), eq("FIXED"),
				eq(true), eq("sofa"), any());

		assertThatThrownBy(() -> catalogService.getServices(null, null, null, null, null, true))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void catalogueReadersSeeInactiveServicesWithTheCommission() {
		catalogReader();
		NexHomeService service = existingService(30L, "FIXED", "400");
		service.setActive(false);
		when(serviceRepository.search(any(), any(), anyBoolean(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(service)));

		NexHomePageResponse<NexHomeServiceResponse> page = catalogService.getServices(0, 20, null, " ", null, true);

		assertThat(page.content().get(0).platformCommissionType()).isEqualTo("PERCENTAGE");
		assertThat(page.content().get(0).platformCommissionValue()).isEqualByComparingTo("15");
		verify(serviceRepository).search(isNull(), isNull(),
				eq(false), isNull(), any());
		verify(lookupService, never()).validateCode(eq("PRICING_TYPE"), any(), any());

		assertThatThrownBy(() -> catalogService.getServices(0, 101, null, null, null, false))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Size must be between 1 and 100.");
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Request validation
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void requestsValidateSlugsCodesMoneyAndMinutes() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			for (String slug : List.of("Home-Cleaning", "home--cleaning", "-home", "home_cleaning", "home cleaning")) {
				assertThat(validator.validate(categoryRequest("Home", slug)))
						.extracting(ConstraintViolation::getMessage)
						.containsExactly("Slug must be lower-case letters and digits separated by single hyphens");
			}
			assertThat(validator.validate(categoryRequest("Home", "deep-cleaning-2-bhk"))).isEmpty();

			assertThat(validator.validate(new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa", null, null, null,
					"FIXED", new BigDecimal("12345678901"), new BigDecimal("1.234"), 0, 0, -5, "FIXED",
					new BigDecimal("-1"), null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Base price must have at most 10 digits and 2 decimals",
							"Minimum charge must have at most 10 digits and 2 decimals",
							"Estimated duration must be more than 0 minutes",
							"Minimum billable time must be more than 0 minutes",
							"Billing increment must be more than 0 minutes",
							"Platform commission value cannot be negative");

			assertThat(validator.validate(new NexHomeServiceVariantCreateRequest(30L, "2bhk", "2 BHK", null,
					new BigDecimal("-0.01"), 0, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Variant code must contain only capital letters, digits and underscores, starting with a letter",
							"Price override cannot be negative",
							"Duration must be more than 0 minutes");

			assertThat(validator.validate(new NexHomeServiceVariantUpdateRequest(40L, 30L, "BHK_2", "NHVAR0001", " ",
					null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Service cannot be updated", "Variant code cannot be updated",
							"Public ID cannot be updated", "Name cannot be blank");

			assertThat(validator.validate(new NexHomeServiceUpdateRequest(30L, "NHSVC0001", null, "", "Bad Slug", null,
					null, null, null, null, null, null, null, null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Public ID cannot be updated", "Name cannot be blank",
							"Slug must be lower-case letters and digits separated by single hyphens");

			assertThat(validator.validate(new NexHomeServiceCategoryUpdateRequest(CATEGORY_ID, null, null, null, null,
					"", null, null))).isEmpty();
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------------------------

	private void catalogReader() {
		when(permissionService.canIfSignedIn("CATALOG_MANAGE", "READ")).thenReturn(true);
	}

	private void activeCategory() {
		when(categoryRepository.findByIdAndActiveTrue(CATEGORY_ID)).thenReturn(Optional.of(category));
	}

	private static NexHomeServiceCategory category(Long id, String name, String slug, boolean active) {
		NexHomeServiceCategory category = new NexHomeServiceCategory();
		category.setId(id);
		category.setName(name);
		category.setSlug(slug);
		category.setActive(active);
		return category;
	}

	private NexHomeService existingService(Long id, String pricingType, String basePrice) {
		NexHomeService service = new NexHomeService();
		service.setId(id);
		service.setCategory(category);
		service.setName("Service " + id);
		service.setSlug("service-" + id);
		service.setPricingType(pricingType);
		service.setBasePrice(new BigDecimal(basePrice));
		service.setPlatformCommissionType("PERCENTAGE");
		service.setPlatformCommissionValue(new BigDecimal("15"));
		when(serviceRepository.findById(id)).thenReturn(Optional.of(service));
		when(serviceRepository.findWithCategoryById(id)).thenReturn(Optional.of(service));
		return service;
	}

	private NexHomeService hourlyService(Long id, String pricePerHour) {
		NexHomeService service = existingService(id, "HOURLY", pricePerHour);
		service.setMinimumBillableMinutes(60);
		service.setBillingIncrementMinutes(30);
		return service;
	}

	private NexHomeServiceVariant existingVariant(Long id, NexHomeService service, boolean active) {
		NexHomeServiceVariant variant = new NexHomeServiceVariant();
		variant.setId(id);
		variant.setService(service);
		variant.setVariantCode("V" + id);
		variant.setName("Variant " + id);
		variant.setActive(active);
		when(variantRepository.findById(id)).thenReturn(Optional.of(variant));
		return variant;
	}

	private static NexHomeServiceCategoryCreateRequest categoryRequest(String name, String slug) {
		return new NexHomeServiceCategoryCreateRequest(name, slug, null, null, null, null);
	}

	private static NexHomeServiceCategoryUpdateRequest categoryUpdate(Long id, String name, String slug) {
		return new NexHomeServiceCategoryUpdateRequest(id, null, name, slug, null, null, null, null);
	}

	private static NexHomeServiceCreateRequest serviceRequest(String pricingType, Integer minimumMinutes,
			Integer incrementMinutes, String commissionType, String commissionValue, Boolean instant,
			Boolean scheduled) {
		return new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa Cleaning", null, null, null, pricingType,
				new BigDecimal("400"), null, null, minimumMinutes, incrementMinutes, commissionType,
				commissionValue == null ? null : new BigDecimal(commissionValue), instant, scheduled, null);
	}

	private static NexHomeServiceUpdateRequest serviceUpdate(Long id, String name, String slug, String pricingType,
			String commissionType, BigDecimal basePrice) {
		return new NexHomeServiceUpdateRequest(id, null, null, name, slug, null, null, pricingType, basePrice, null,
				null, null, null, commissionType, null, null, null, null);
	}
}
