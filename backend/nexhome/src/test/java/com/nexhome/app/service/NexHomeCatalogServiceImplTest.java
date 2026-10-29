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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeServiceCategoryCreateRequest;
import com.nexhome.app.dto.NexHomeServiceCategoryUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceCreateRequest;
import com.nexhome.app.dto.NexHomeServiceMediaUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.dto.NexHomeServiceResponse;
import com.nexhome.app.dto.NexHomeServiceUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantCreateRequest;
import com.nexhome.app.dto.NexHomeServiceVariantInput;
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
	private NexHomeServiceImageRepository imageRepository;
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
		imageRepository = mock(NexHomeServiceImageRepository.class);
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
				mediaRepository, imageRepository, requirementRepository, faqRepository, partnerProfileRepository,
				partnerServiceRepository, lookupService, permissionService, new NexHomeServiceCategoryMapper(),
				new NexHomeServiceMapper(), new NexHomeServiceVariantMapper(), new NexHomeServiceMediaMapper(),
				new NexHomeServiceRequirementMapper(), mock(NexHomeAdminProfileRepository.class),
				mock(NexHomeUserRepository.class));
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
	void fixedAndInspectionChargeTheUnitPriceOnceIgnoringMinutes() {
		NexHomeService fixed = existingService(30L, "FIXED", "499.5");
		fixed.setEstimatedDurationMinutes(120);
		fixed.setMinimumCharge(new BigDecimal("600"));

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 500);

		assertThat(quote.subtotal()).isEqualByComparingTo("499.50");
		assertThat(quote.regularSubtotal()).isEqualByComparingTo("499.50");
		assertThat(quote.subtotal().scale()).isEqualTo(2);
		assertThat(quote.total()).isEqualByComparingTo("600");
		assertThat(quote.regularPrice()).isEqualByComparingTo("499.5");
		assertThat(quote.billableMinutes()).isNull();
		assertThat(quote.durationMinutes()).isEqualTo(120);
		assertThat(quote.serviceVariantId()).isNull();

		existingService(31L, "INSPECTION", "199");
		NexHomeServiceQuoteResponse inspection = catalogService.quote(31L, null, null);
		assertThat(inspection.subtotal()).isEqualByComparingTo("199.00");
		assertThat(inspection.total()).isEqualByComparingTo("199.00");
		assertThat(inspection.pricingType()).isEqualTo("INSPECTION");
	}

	@Test
	void unknownPricingTypeIsChargedLikeFixed() {
		existingService(30L, "SUBSCRIPTION", "250");

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 90);

		assertThat(quote.subtotal()).isEqualByComparingTo("250.00");
		assertThat(quote.billableMinutes()).isNull();
	}

	@Test
	void areaChargesTheHomeSizeVariantOnceAndDiscountsApply() {
		NexHomeService area = existingService(30L, "AREA", "799");
		area.setDiscountPrice(new BigDecimal("699"));
		NexHomeServiceVariant twoBhk = existingVariant(40L, area, true);
		twoBhk.setPriceOverride(new BigDecimal("1199"));
		twoBhk.setDiscountPrice(new BigDecimal("1100"));

		NexHomeServiceQuoteResponse withoutVariant = catalogService.quote(30L, null, 500);
		assertThat(withoutVariant.regularPrice()).isEqualByComparingTo("799");
		assertThat(withoutVariant.unitPrice()).isEqualByComparingTo("699");
		assertThat(withoutVariant.total()).isEqualByComparingTo("699.00");
		assertThat(withoutVariant.billableMinutes()).isNull();

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, null);
		assertThat(quote.regularPrice()).isEqualByComparingTo("1199");
		assertThat(quote.unitPrice()).isEqualByComparingTo("1100");
		assertThat(quote.total()).isEqualByComparingTo("1100.00");

		// Without its own discount a variant charges its price, not the service's discount
		twoBhk.setDiscountPrice(null);
		assertThat(catalogService.quote(30L, 40L, null).total()).isEqualByComparingTo("1199.00");
	}

	@Test
	void hourlyVariantWithHoursIsAPackageAndLongerJobsAddTheHourlyPrice() {
		NexHomeService hourly = hourlyService(30L, "400");
		hourly.setDiscountPrice(new BigDecimal("300"));
		hourly.setEstimatedDurationMinutes(120);
		NexHomeServiceVariant variant = existingVariant(40L, hourly, true);
		variant.setPriceOverride(new BigDecimal("699"));
		variant.setDurationMinutes(180);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, null);

		assertThat(quote.unitPrice()).isEqualByComparingTo("699");
		assertThat(quote.billableMinutes()).isEqualTo(180);
		assertThat(quote.subtotal()).isEqualByComparingTo("699.00");
		assertThat(quote.durationMinutes()).isEqualTo(180);
		assertThat(quote.serviceVariantId()).isEqualTo(40L);
		// Less time than the package still costs the package
		assertThat(catalogService.quote(30L, 40L, 60).subtotal()).isEqualByComparingTo("699.00");

		// 200 minutes: 20 extra, billed as one 30-minute increment at the discounted 300 / hour
		NexHomeServiceQuoteResponse longer = catalogService.quote(30L, 40L, 200);
		assertThat(longer.billableMinutes()).isEqualTo(210);
		assertThat(longer.subtotal()).isEqualByComparingTo("849.00");
		// At the regular prices: the package (no discount of its own) plus 30 minutes at the regular 400 / hour
		assertThat(longer.regularSubtotal()).isEqualByComparingTo("899.00");

		// A discounted package: 200 regular, 160 charged, so 40 off
		variant.setPriceOverride(new BigDecimal("200"));
		variant.setDiscountPrice(new BigDecimal("160"));
		NexHomeServiceQuoteResponse discounted = catalogService.quote(30L, 40L, null);
		assertThat(discounted.subtotal()).isEqualByComparingTo("160.00");
		assertThat(discounted.regularSubtotal()).isEqualByComparingTo("200.00");
	}

	@Test
	void hourlyVariantWithoutHoursIsAnHourlyRate() {
		NexHomeService hourly = hourlyService(30L, "400");
		NexHomeServiceVariant variant = existingVariant(40L, hourly, true);
		variant.setPriceOverride(new BigDecimal("600"));

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, 70);

		assertThat(quote.unitPrice()).isEqualByComparingTo("600");
		assertThat(quote.billableMinutes()).isEqualTo(90);
		assertThat(quote.subtotal()).isEqualByComparingTo("900.00");
	}

	@Test
	void hourlyRoundsTheSubtotalHalfUpToPaise() {
		NexHomeService hourly = hourlyService(30L, "100");
		hourly.setBillingIncrementMinutes(1);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, 61);

		assertThat(quote.billableMinutes()).isEqualTo(61);
		assertThat(quote.subtotal()).isEqualByComparingTo("101.67");
	}

	@Test
	void hourlyWithoutAPackageOrMinutesIsOneHourAtTheBasicPrice() {
		hourlyService(30L, "400");

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, null, null);
		assertThat(quote.billableMinutes()).isEqualTo(60);
		assertThat(quote.total()).isEqualByComparingTo("400");
		assertThatThrownBy(() -> catalogService.quote(30L, null, -30))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Minutes must be more than 0 to price an hourly service.");
	}

	@Test
	void variantWithoutOverridesUsesTheServicePriceAndDuration() {
		NexHomeService fixed = existingService(30L, "FIXED", "799");
		fixed.setEstimatedDurationMinutes(45);
		existingVariant(40L, fixed, true);

		NexHomeServiceQuoteResponse quote = catalogService.quote(30L, 40L, null);

		assertThat(quote.unitPrice()).isEqualByComparingTo("799");
		assertThat(quote.durationMinutes()).isEqualTo(45);
	}

	@Test
	void variantMustBelongToTheServiceBeforeItsStateIsChecked() {
		existingService(30L, "FIXED", "799");
		NexHomeService other = existingService(31L, "FIXED", "999");
		existingVariant(41L, other, false);
		when(variantRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.quote(30L, 41L, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The variant does not belong to this service.");
		assertThatThrownBy(() -> catalogService.quote(30L, 404L, null))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service variant does not exist.");
	}

	@Test
	void inactiveVariantCanBeQuotedByCatalogueReaders() {
		NexHomeService fixed = existingService(30L, "FIXED", "799");
		NexHomeServiceVariant variant = existingVariant(40L, fixed, false);
		variant.setPriceOverride(new BigDecimal("1299"));

		assertThatThrownBy(() -> catalogService.quote(30L, 40L, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The variant is not available.");

		catalogReader();
		assertThat(catalogService.quote(30L, 40L, null).total()).isEqualByComparingTo("1299.00");
	}

	@Test
	void inactiveServiceOrCategoryIsNotFoundForThePublic() {
		NexHomeService inactive = existingService(30L, "FIXED", "799");
		inactive.setActive(false);
		NexHomeService inInactiveCategory = existingService(31L, "FIXED", "799");
		inInactiveCategory.setCategory(category(9L, "Old", "old", false));
		when(serviceRepository.findWithCategoryById(404L)).thenReturn(Optional.empty());

		for (Long serviceId : List.of(30L, 31L, 404L)) {
			assertThatThrownBy(() -> catalogService.quote(serviceId, null, null))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessage("Service does not exist.");
		}

		catalogReader();
		assertThat(catalogService.quote(30L, null, null).total()).isEqualByComparingTo("799.00");
		assertThat(catalogService.quote(31L, null, null).total()).isEqualByComparingTo("799.00");
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
	void serviceTargetAllowsOnlyItsPricingTypes() {
		activeCategory();
		serviceTargets();

		assertThatThrownBy(() -> catalogService.createService(serviceCreate("APPLIANCE", "AREA", null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A service for Appliance / Device can be priced only as: FIXED, HOURLY.");
		verify(serviceRepository, never()).save(any());

		assertThat(catalogService.createService(serviceCreate("PROPERTY", "AREA", null, null)).serviceTarget())
				.isEqualTo("PROPERTY");

		// Changing only the target is checked against the current pricing type, before anything changes
		NexHomeService service = existingService(31L, "AREA", "799");
		service.setServiceTarget("PROPERTY");
		assertThatThrownBy(() -> catalogService.updateService(new NexHomeServiceUpdateRequest(31L, null, null, null,
				null, null, null, "APPLIANCE", null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null))).isInstanceOf(InvalidRequestException.class);
		assertThat(service.getServiceTarget()).isEqualTo("PROPERTY");
		assertThat(service.getUpdatedBy()).isNull();
	}

	@Test
	void discountPriceIsAtMostThePriceAndAlwaysReplacedOnUpdate() {
		activeCategory();
		NexHomeServiceCreateRequest tooHigh = new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa", null, null, null,
				"PROPERTY", "FIXED", new BigDecimal("500"), new BigDecimal("501"), null, null, null, null, "FIXED",
				null, null, null, null, null, null, null);
		assertThatThrownBy(() -> catalogService.createService(tooHigh))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Discount price cannot be more than the base price.");

		NexHomeService service = existingService(30L, "FIXED", "500");
		service.setServiceTarget("PROPERTY");
		service.setDiscountPrice(new BigDecimal("450"));
		catalogService.updateService(serviceUpdate(30L, "Sofa", null, null, null, null));
		assertThat(service.getDiscountPrice()).isNull();
	}

	@Test
	void newServiceGetsItsVariantsWithCodesFromTheirNames() {
		activeCategory();
		List<NexHomeServiceVariantInput> variants = List.of(
				new NexHomeServiceVariantInput(null, "2 Hours", "Small homes", new BigDecimal("499"),
						new BigDecimal("450"), 120, null),
				new NexHomeServiceVariantInput(null, "2 hours!", null, new BigDecimal("599"), null, 120, false));

		catalogService.createService(serviceCreate("PROPERTY", "HOURLY", variants, null));

		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<NexHomeServiceVariant>> saved = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(variantRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(NexHomeServiceVariant::getVariantCode)
				.containsExactly("V_2_HOURS", "V_2_HOURS_2");
		assertThat(saved.getValue()).extracting(NexHomeServiceVariant::getDisplayOrder).containsExactly(0, 1);
		assertThat(saved.getValue().get(0).getDiscountPrice()).isEqualByComparingTo("450");
		assertThat(saved.getValue().get(1).isActive()).isFalse();
		assertThat(saved.getValue().get(1).getCreatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void variantRulesAreCheckedBeforeAnythingIsSaved() {
		activeCategory();
		List<NexHomeServiceVariantInput> discounted = List.of(new NexHomeServiceVariantInput(null, "1 BHK", null,
				new BigDecimal("799"), new BigDecimal("800"), null, null));
		assertThatThrownBy(() -> catalogService.createService(serviceCreate("PROPERTY", "AREA", discounted, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The discount price of 1 BHK cannot be more than its price.");

		List<NexHomeServiceVariantInput> withoutHours = List.of(new NexHomeServiceVariantInput(null, "Visit", null,
				new BigDecimal("499"), null, null, null));
		assertThatThrownBy(() -> catalogService.createService(serviceCreate("PROPERTY", "HOURLY", withoutHours,
				null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Give Visit the hours its price covers.");

		List<NexHomeServiceVariantInput> withId = List.of(new NexHomeServiceVariantInput(4L, "1 BHK", null,
				new BigDecimal("799"), null, null, null));
		assertThatThrownBy(() -> catalogService.createService(serviceCreate("PROPERTY", "AREA", withId, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The variants of a new service cannot have a service variant ID.");
		verify(serviceRepository, never()).save(any());
	}

	@Test
	void updateSyncsTheVariantsAndRejectsAnotherServicesVariant() {
		NexHomeService service = existingService(30L, "AREA", "799");
		service.setServiceTarget("PROPERTY");
		NexHomeServiceVariant kept = existingVariant(40L, service, true);
		kept.setVariantCode("ONE_BHK");
		NexHomeServiceVariant removed = existingVariant(41L, service, true);
		removed.setVariantCode("TWO_BHK");
		when(variantRepository.findByServiceIdOrderByDisplayOrderAscNameAsc(30L)).thenReturn(List.of(kept, removed));

		assertThatThrownBy(() -> catalogService.updateService(serviceUpdateWithVariants(30L, List.of(
				new NexHomeServiceVariantInput(99L, "Other", null, BigDecimal.ONE, null, null, null)))))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Service variant 99 is not a variant of this service.");
		assertThat(service.getUpdatedBy()).isNull();

		catalogService.updateService(serviceUpdateWithVariants(30L, List.of(
				new NexHomeServiceVariantInput(null, "One BHK", null, new BigDecimal("1599"), null, null, null),
				new NexHomeServiceVariantInput(40L, "1 BHK", "Up to 600 sq ft", new BigDecimal("899"),
						new BigDecimal("850"), null, null))));

		assertThat(kept.getName()).isEqualTo("1 BHK");
		assertThat(kept.getPriceOverride()).isEqualByComparingTo("899");
		assertThat(kept.getDiscountPrice()).isEqualByComparingTo("850");
		assertThat(kept.getDisplayOrder()).isEqualTo(1);
		verify(variantRepository).deleteAll(List.of(removed));
		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<NexHomeServiceVariant>> added = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(variantRepository).saveAll(added.capture());
		// ONE_BHK is taken by the kept variant
		assertThat(added.getValue()).extracting(NexHomeServiceVariant::getVariantCode).containsExactly("ONE_BHK_2");
	}

	@Test
	void chosenRequirementsAreSyncedPerSideKeepingTheRestAndTheirOrder() {
		NexHomeService service = existingService(30L, "FIXED", "500");
		service.setServiceTarget("PROPERTY");
		when(lookupService.getActiveLookupValues("CUSTOMER_REQUIREMENT")).thenReturn(List.of(
				lookupValue("CUSTOMER_REQUIREMENT", "WATER_SUPPLY", "Water supply available",
						Map.of("requirementType", "PREPARATION")),
				lookupValue("CUSTOMER_REQUIREMENT", "PARKING", "Parking", Map.of())));
		NexHomeServiceRequirement water = new NexHomeServiceRequirement();
		water.setRequirementCode("WATER_SUPPLY");
		NexHomeServiceRequirement gate = new NexHomeServiceRequirement();
		gate.setRequirementCode("GATE_ACCESS");
		when(requirementRepository.findByServiceIdAndRequirementForAndRequirementCodeIsNotNull(30L, "CUSTOMER"))
				.thenReturn(List.of(water, gate));

		assertThatThrownBy(() -> catalogService.updateService(serviceUpdateWithRequirements(30L,
				List.of("LADDER")))).isInstanceOf(InvalidRequestException.class)
				.hasMessage("Customer requirement must be one of: WATER_SUPPLY, PARKING.");

		catalogService.updateService(serviceUpdateWithRequirements(30L, List.of(" PARKING ", "WATER_SUPPLY",
				"PARKING")));

		assertThat(water.getDisplayOrder()).isEqualTo(1);
		verify(requirementRepository).deleteAll(List.of(gate));
		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<NexHomeServiceRequirement>> added =
				org.mockito.ArgumentCaptor.forClass(List.class);
		verify(requirementRepository).saveAll(added.capture());
		NexHomeServiceRequirement parking = added.getValue().get(0);
		assertThat(added.getValue()).hasSize(1);
		assertThat(parking.getRequirementCode()).isEqualTo("PARKING");
		assertThat(parking.getRequirementFor()).isEqualTo("CUSTOMER");
		assertThat(parking.getDescription()).isEqualTo("Parking");
		assertThat(parking.getRequirementType()).isEqualTo("OTHER");
		assertThat(parking.isMandatory()).isTrue();
		assertThat(parking.getDisplayOrder()).isZero();
		// The partner side was not sent, so it is left alone
		verify(requirementRepository, never())
				.findByServiceIdAndRequirementForAndRequirementCodeIsNotNull(30L, "PARTNER");
	}

	@Test
	void variantCodesComeFromTheNames() {
		Set<String> taken = new HashSet<>(Set.of("SPLIT_AC"));
		assertThat(NexHomeCatalogServiceImpl.variantCodeFor("Split AC", taken)).isEqualTo("SPLIT_AC_2");
		assertThat(NexHomeCatalogServiceImpl.variantCodeFor("Split AC", taken)).isEqualTo("SPLIT_AC_3");
		assertThat(NexHomeCatalogServiceImpl.variantCodeFor("  Crème brûlée -- XL ", taken))
				.isEqualTo("CREME_BRULEE_XL");
		assertThat(NexHomeCatalogServiceImpl.variantCodeFor("3 BHK", taken)).isEqualTo("V_3_BHK");
		assertThat(NexHomeCatalogServiceImpl.variantCodeFor("पूजा", taken)).isEqualTo("VARIANT");
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Images
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void imageTypeComesFromTheBytes() {
		assertThat(NexHomeCatalogServiceImpl.imageContentType(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
		assertThat(NexHomeCatalogServiceImpl.imageContentType(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)))
				.isEqualTo("image/png");
		assertThat(NexHomeCatalogServiceImpl.imageContentType(
				bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'))).isEqualTo("image/webp");
		assertThat(NexHomeCatalogServiceImpl.imageContentType("<svg onload=alert(1)>".getBytes())).isNull();
		assertThat(NexHomeCatalogServiceImpl.imageContentType(bytes('G', 'I', 'F', '8', '9', 'a'))).isNull();
		assertThat(NexHomeCatalogServiceImpl.imageContentType(new byte[0])).isNull();
	}

	@Test
	void imageFileNameLosesItsPath() {
		assertThat(NexHomeCatalogServiceImpl.imageFileName("C:\\photos\\sofa.jpg")).isEqualTo("sofa.jpg");
		assertThat(NexHomeCatalogServiceImpl.imageFileName("../../etc/x.png")).isEqualTo("x.png");
		assertThat(NexHomeCatalogServiceImpl.imageFileName(null)).isEqualTo("image");
		assertThat(NexHomeCatalogServiceImpl.imageFileName("a/")).isEqualTo("image");
		assertThat(NexHomeCatalogServiceImpl.imageFileName("x".repeat(300) + ".jpg")).hasSize(255).endsWith(".jpg");
	}

	@Test
	void imagesAreLimitedInNumberSizeAndType() {
		NexHomeService service = existingService(30L, "FIXED", "500");
		when(serviceRepository.findLockedById(30L)).thenReturn(Optional.of(service));
		byte[] jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00);
		when(imageRepository.countByServiceId(30L)).thenReturn(4L);
		when(imageRepository.save(any())).thenAnswer(invocation -> {
			com.nexhome.app.model.NexHomeServiceImage saved = invocation.getArgument(0);
			saved.setId(70L);
			saved.setCreationDate(Instant.now());
			return saved;
		});

		var image = catalogService.addImage(30L, "sofa.jpg", jpeg);
		assertThat(image.imageUrl()).isEqualTo("/api/catalog/services/images/70");
		assertThat(image.contentType()).isEqualTo("image/jpeg");
		assertThat(image.displayOrder()).isEqualTo(4);

		when(imageRepository.countByServiceId(30L)).thenReturn(5L);
		assertThatThrownBy(() -> catalogService.addImage(30L, "sixth.jpg", jpeg))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A service can have at most 5 images.");
		assertThatThrownBy(() -> catalogService.addImage(30L, "a.gif", bytes('G', 'I', 'F', '8')))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Image must be a JPEG, PNG or WebP file.");
		assertThatThrownBy(() -> catalogService.addImage(30L, "big.jpg", new byte[5 * 1024 * 1024 + 1]))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Image must be at most 5 MB.");
		assertThatThrownBy(() -> catalogService.addImage(30L, "empty.jpg", new byte[0]))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Choose an image to upload.");
		when(serviceRepository.findLockedById(404L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> catalogService.addImage(404L, "a.jpg", jpeg))
				.isInstanceOf(ResourceNotFoundException.class);
	}

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
		assertThatThrownBy(() -> catalogService.createService(new NexHomeServiceCreateRequest(CATEGORY_ID, "---", null, null, null, "PROPERTY", "FIXED", BigDecimal.TEN, null, null, null, null, null, "FIXED", null, null, null, null, null, null, null)))
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

		assertThatThrownBy(() -> catalogService.createService(new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa Cleaning", null, null, null, "PROPERTY", "FIXED", BigDecimal.TEN, null, null, null, null, null, "FIXED", null, null, null, null, null, null, null)))
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

		NexHomeServiceResponse response = catalogService.updateService(new NexHomeServiceUpdateRequest(30L, null, 8L, "Fan Repair", "fan-repair", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

		assertThat(response.categoryId()).isEqualTo(8L);
		assertThat(response.slug()).isEqualTo("fan-repair");
		assertThat(service.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void unknownLookupCodeOnUpdateLeavesTheServiceUnchanged() {
		NexHomeService service = existingService(30L, "FIXED", "400");
		doThrow(new InvalidRequestException("Pricing type must be one of: FIXED, HOURLY, AREA, INSPECTION."))
				.when(lookupService).validateCode("PRICING_TYPE", "DAILY", "Pricing type");
		when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> catalogService.updateService(serviceUpdate(30L, "Renamed", null, "DAILY", null,
				new BigDecimal("1")))).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> catalogService.updateService(new NexHomeServiceUpdateRequest(30L, null, 99L, "Renamed", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
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

		var response = catalogService.updateVariant(new NexHomeServiceVariantUpdateRequest(40L, null, null, null, null, null, null, null, null, null, null));

		assertThat(response.priceOverride()).isNull();
		assertThat(response.durationMinutes()).isNull();
		assertThat(response.name()).isEqualTo("2 BHK");
	}

	@Test
	void variantCodeIsTrimmedBeforeTheDuplicateCheck() {
		existingService(30L, "FIXED", "400");
		when(variantRepository.existsByServiceIdAndVariantCode(30L, "BHK_2")).thenReturn(true);

		assertThatThrownBy(() -> catalogService.addVariant(new NexHomeServiceVariantCreateRequest(30L, " BHK_2 ", "2 BHK", null, null, null, null, null, null)))
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
				used -> when(imageRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(requirementRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(faqRepository.existsByServiceId(30L)).thenReturn(used),
				used -> when(partnerServiceRepository.existsByServiceId(30L)).thenReturn(used));

		for (Consumer<Boolean> use : uses) {
			use.accept(true);
			assertThatThrownBy(() -> catalogService.deleteService(30L))
					.isInstanceOf(DuplicateResourceException.class)
					.hasMessage(
							"Service has variants, images, media, requirements, FAQs or partners; deactivate it instead.");
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
		when(serviceRepository.search(any(), any(), any(), anyBoolean(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(service)));

		NexHomePageResponse<NexHomeServiceResponse> page = catalogService.getServices(null, null, CATEGORY_ID,
				" FIXED ", null, "  Sofa ", false);

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).platformCommissionType()).isNull();
		assertThat(page.content().get(0).platformCommissionValue()).isNull();
		assertThat(page.size()).isEqualTo(10);
		verify(serviceRepository).search(eq(CATEGORY_ID), eq("FIXED"), isNull(),
				eq(true), eq("sofa"), any());

		assertThatThrownBy(() -> catalogService.getServices(null, null, null, null, null, null, true))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void catalogueReadersSeeInactiveServicesWithTheCommission() {
		catalogReader();
		NexHomeService service = existingService(30L, "FIXED", "400");
		service.setActive(false);
		when(serviceRepository.search(any(), any(), any(), anyBoolean(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(service)));

		NexHomePageResponse<NexHomeServiceResponse> page = catalogService.getServices(0, 20, null, " ", false, null,
				true);

		assertThat(page.content().get(0).platformCommissionType()).isEqualTo("PERCENTAGE");
		assertThat(page.content().get(0).platformCommissionValue()).isEqualByComparingTo("15");
		verify(serviceRepository).search(isNull(), isNull(), eq(false),
				eq(false), isNull(), any());
		verify(lookupService, never()).validateCode(eq("PRICING_TYPE"), any(), any());

		assertThatThrownBy(() -> catalogService.getServices(0, 101, null, null, null, null, false))
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

			assertThat(validator.validate(new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa", null, null, null, "PROPERTY", "FIXED", new BigDecimal("12345678901"), null, new BigDecimal("1.234"), 0, 0, -5, "FIXED", new BigDecimal("-1"), null, null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Base price must have at most 10 digits and 2 decimals",
							"Minimum charge must have at most 10 digits and 2 decimals",
							"Estimated duration must be more than 0 minutes",
							"Minimum billable time must be more than 0 minutes",
							"Billing increment must be more than 0 minutes",
							"Platform commission value cannot be negative");

			assertThat(validator.validate(new NexHomeServiceVariantCreateRequest(30L, "2bhk", "2 BHK", null, new BigDecimal("-0.01"), null, 0, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Variant code must contain only capital letters, digits and underscores, starting with a letter",
							"Price override cannot be negative",
							"Duration must be more than 0 minutes");

			assertThat(validator.validate(new NexHomeServiceVariantUpdateRequest(40L, 30L, "BHK_2", "NHVAR0001", " ", null, null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Service cannot be updated", "Variant code cannot be updated",
							"Public ID cannot be updated", "Name cannot be blank");

			assertThat(validator.validate(new NexHomeServiceUpdateRequest(30L, "NHSVC0001", null, "", "Bad Slug", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
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

	private static byte[] bytes(int... values) {
		byte[] data = new byte[values.length];
		for (int index = 0; index < values.length; index++) {
			data[index] = (byte) values[index];
		}
		return data;
	}

	private static NexHomeLookupValueResponse lookupValue(String typeCode, String valueCode, String displayName,
			Map<String, Object> metadata) {
		return new NexHomeLookupValueResponse(1L, 1L, typeCode, valueCode, displayName, null, 0, true, false,
				metadata, Instant.now());
	}

	private void serviceTargets() {
		when(lookupService.getActiveLookupValues("SERVICE_TARGET")).thenReturn(List.of(
				lookupValue("SERVICE_TARGET", "APPLIANCE", "Appliance / Device",
						Map.of("pricingTypes", List.of("FIXED", "HOURLY"))),
				lookupValue("SERVICE_TARGET", "PROPERTY", "Home / Property",
						Map.of("pricingTypes", List.of("FIXED", "HOURLY", "AREA")))));
	}

	private static NexHomeServiceCreateRequest serviceCreate(String serviceTarget, String pricingType,
			List<NexHomeServiceVariantInput> variants, List<String> customerRequirements) {
		boolean hourly = "HOURLY".equals(pricingType);
		return new NexHomeServiceCreateRequest(CATEGORY_ID, "Deep Cleaning", null, null, null, serviceTarget,
				pricingType, new BigDecimal("499"), null, null, null, hourly ? 60 : null, hourly ? 30 : null,
				"PERCENTAGE", new BigDecimal("15"), null, null, null, variants, customerRequirements, null);
	}

	private static NexHomeServiceUpdateRequest serviceUpdateWithVariants(Long serviceId,
			List<NexHomeServiceVariantInput> variants) {
		return new NexHomeServiceUpdateRequest(serviceId, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, variants, null, null);
	}

	private static NexHomeServiceUpdateRequest serviceUpdateWithRequirements(Long serviceId,
			List<String> customerRequirements) {
		return new NexHomeServiceUpdateRequest(serviceId, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, customerRequirements, null);
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
		return new NexHomeServiceCreateRequest(CATEGORY_ID, "Sofa Cleaning", null, null, null, "PROPERTY", pricingType, new BigDecimal("400"), null, null, null, minimumMinutes, incrementMinutes, commissionType, commissionValue == null ? null : new BigDecimal(commissionValue), instant, scheduled, null, null, null, null);
	}

	private static NexHomeServiceUpdateRequest serviceUpdate(Long id, String name, String slug, String pricingType,
			String commissionType, BigDecimal basePrice) {
		return new NexHomeServiceUpdateRequest(id, null, null, name, slug, null, null, null, pricingType, basePrice, null, null, null, null, null, commissionType, null, null, null, null, null, null, null);
	}
}
