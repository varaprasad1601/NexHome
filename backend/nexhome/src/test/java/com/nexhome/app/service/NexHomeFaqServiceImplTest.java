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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nexhome.app.dto.NexHomeFaqCreateRequest;
import com.nexhome.app.dto.NexHomeFaqResponse;
import com.nexhome.app.dto.NexHomeFaqUpdateRequest;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeFaqMapper;
import com.nexhome.app.model.NexHomeFaq;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.repository.NexHomeFaqRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NexHomeFaqServiceImplTest {

	private static final Long ACTING_USER_ID = 1L;

	private NexHomeFaqRepository faqRepository;
	private NexHomeServiceCategoryRepository categoryRepository;
	private NexHomeServiceRepository serviceRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomeFaqServiceImpl faqService;

	@BeforeEach
	void setUp() {
		faqRepository = mock(NexHomeFaqRepository.class);
		categoryRepository = mock(NexHomeServiceCategoryRepository.class);
		serviceRepository = mock(NexHomeServiceRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);
		when(faqRepository.search(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));
		when(faqRepository.save(any())).thenAnswer(invocation -> {
			NexHomeFaq faq = invocation.getArgument(0);
			faq.setId(80L);
			return faq;
		});
		when(lookupService.validateCode(eq("FAQ_AUDIENCE"), any(), eq("Audience")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		when(permissionService.currentUserId()).thenReturn(ACTING_USER_ID);
		faqService = new NexHomeFaqServiceImpl(faqRepository, categoryRepository, serviceRepository, lookupService,
				permissionService, new NexHomeFaqMapper());
	}

	@Test
	void publishedFaqsForAnAudienceAlsoIncludeBoth() {
		faqService.getPublishedFaqs(" CUSTOMER ", 5L, null);
		faqService.getPublishedFaqs("PARTNER", null, 30L);
		faqService.getPublishedFaqs("BOTH", null, null);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> audiences = ArgumentCaptor.forClass(Collection.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(faqRepository, org.mockito.Mockito.times(3)).search(audiences.capture(), any(), any(), eq(true),
				isNull(), eq(true), pageable.capture());
		assertThat(audiences.getAllValues().get(0)).containsExactly("CUSTOMER", "BOTH");
		assertThat(audiences.getAllValues().get(1)).containsExactly("PARTNER", "BOTH");
		assertThat(audiences.getAllValues().get(2)).containsExactly("BOTH");
		assertThat(pageable.getValue().isPaged()).isFalse();
		assertThat(pageable.getValue().getSort())
				.isEqualTo(Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("id")));
		verify(faqRepository).search(any(), eq(5L), isNull(), eq(true), isNull(), eq(true), any());
		verify(faqRepository).search(any(), isNull(), eq(30L), eq(true), isNull(), eq(true), any());
	}

	@Test
	void publishedFaqsWithoutAnAudienceAreNotFilteredByIt() {
		faqService.getPublishedFaqs("  ", null, null);
		faqService.getPublishedFaqs(null, null, null);

		verify(faqRepository, org.mockito.Mockito.times(2)).search(isNull(), isNull(), isNull(), eq(true), isNull(),
				eq(true), any());
		verify(lookupService, never()).validateCode(any(), any(), any());
	}

	@Test
	void adminListMatchesTheAudienceExactly() {
		faqService.getFaqs(null, null, "CUSTOMER", null, null, false, "  Refund ");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> audiences = ArgumentCaptor.forClass(Collection.class);
		verify(faqRepository).search(audiences.capture(), isNull(), isNull(), eq(false), eq("refund"), eq(false),
				any());
		assertThat(audiences.getValue()).containsExactly("CUSTOMER");

		assertThatThrownBy(() -> faqService.getFaqs(-1, null, null, null, null, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Page must be 0 or more.");
	}

	@Test
	void faqBelongsToACategoryOrAServiceNotBoth() {
		assertThatThrownBy(() -> faqService.createFaq(create(5L, 30L, "CUSTOMER")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("An FAQ belongs to a category or a service, not both.");
		verify(categoryRepository, never()).findById(any());
		verify(serviceRepository, never()).findById(any());
		verify(faqRepository, never()).save(any());
	}

	@Test
	void missingCategoryOrServiceIsNotFound() {
		when(categoryRepository.findById(404L)).thenReturn(Optional.empty());
		when(serviceRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> faqService.createFaq(create(404L, null, "CUSTOMER")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service category does not exist.");
		assertThatThrownBy(() -> faqService.createFaq(create(null, 404L, "CUSTOMER")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Service does not exist.");
		verify(faqRepository, never()).save(any());
	}

	@Test
	void faqIsCreatedUnpublishedWithTheTrimmedAudience() {
		NexHomeServiceCategory category = new NexHomeServiceCategory();
		category.setId(5L);
		when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
		NexHomeService service = new NexHomeService();
		service.setId(30L);
		when(serviceRepository.findById(30L)).thenReturn(Optional.of(service));

		NexHomeFaqResponse general = faqService.createFaq(create(null, null, " BOTH "));
		assertThat(general.audience()).isEqualTo("BOTH");
		assertThat(general.isPublished()).isFalse();
		assertThat(general.categoryId()).isNull();
		assertThat(general.serviceId()).isNull();
		assertThat(general.question()).isEqualTo("How do I cancel?");

		assertThat(faqService.createFaq(create(5L, null, "CUSTOMER")).categoryId()).isEqualTo(5L);
		assertThat(faqService.createFaq(create(null, 30L, "PARTNER")).serviceId()).isEqualTo(30L);
	}

	@Test
	void unknownAudienceIsRejectedBeforeAnythingChanges() {
		doThrow(new InvalidRequestException("Audience must be one of: CUSTOMER, PARTNER, BOTH."))
				.when(lookupService).validateCode("FAQ_AUDIENCE", "EVERYONE", "Audience");
		NexHomeFaq faq = existingFaq();

		assertThatThrownBy(() -> faqService.createFaq(create(null, null, "EVERYONE")))
				.isInstanceOf(InvalidRequestException.class);
		verify(faqRepository, never()).save(any());

		assertThatThrownBy(() -> faqService.updateFaq(new NexHomeFaqUpdateRequest(80L, null, null, "Changed?", null,
				"EVERYONE", null, true))).isInstanceOf(InvalidRequestException.class);
		assertThat(faq.getQuestion()).isEqualTo("How do I cancel?");
		assertThat(faq.isPublished()).isFalse();
		assertThat(faq.getUpdatedBy()).isNull();
	}

	@Test
	void updateKeepsMissingFieldsAndAppliesTheAudience() {
		NexHomeFaq faq = existingFaq();

		NexHomeFaqResponse response = faqService.updateFaq(new NexHomeFaqUpdateRequest(80L, null, null, null,
				"  Within 24 hours. ", " PARTNER ", null, true));

		assertThat(response.question()).isEqualTo("How do I cancel?");
		assertThat(response.answer()).isEqualTo("Within 24 hours.");
		assertThat(response.audience()).isEqualTo("PARTNER");
		assertThat(response.isPublished()).isTrue();
		assertThat(faq.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void missingFaqIsNotFound() {
		when(faqRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> faqService.deleteFaq(404L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("FAQ does not exist.");
		verify(faqRepository, never()).delete(any(NexHomeFaq.class));
	}

	@Test
	void requestsRejectCategoryAndServiceChanges() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomeFaqUpdateRequest(80L, 5L, 30L, " ", "", null, null,
					null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Category cannot be updated", "Service cannot be updated",
							"Question cannot be blank", "Answer cannot be blank");
			assertThat(factory.getValidator().validate(new NexHomeFaqCreateRequest(" ", null, null, null, null, null,
					null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Question is required", "Answer is required", "Audience is required");
		}
	}

	private static NexHomeFaqCreateRequest create(Long categoryId, Long serviceId, String audience) {
		return new NexHomeFaqCreateRequest(" How do I cancel? ", "From the booking page.", categoryId, serviceId,
				audience, null, null);
	}

	private NexHomeFaq existingFaq() {
		NexHomeFaq faq = new NexHomeFaq();
		faq.setId(80L);
		faq.setQuestion("How do I cancel?");
		faq.setAnswer("From the booking page.");
		faq.setAudience("CUSTOMER");
		when(faqRepository.findById(80L)).thenReturn(Optional.of(faq));
		return faq;
	}
}
