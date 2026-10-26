package com.nexhome.app.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeFaqCreateRequest;
import com.nexhome.app.dto.NexHomeFaqResponse;
import com.nexhome.app.dto.NexHomeFaqUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeFaqMapper;
import com.nexhome.app.model.NexHomeFaq;
import com.nexhome.app.model.NexHomeLookupCodes.Audience;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.repository.NexHomeFaqRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeFaqServiceImpl implements NexHomeFaqService {

	private final NexHomeFaqRepository faqRepository;
	private final NexHomeServiceCategoryRepository categoryRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeFaqMapper faqMapper;

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeFaqResponse> getPublishedFaqs(String audience, Long categoryId, Long serviceId) {
		List<String> audiences = null;
		if (audience != null && !audience.isBlank()) {
			String code = lookupService.validateCode(Types.FAQ_AUDIENCE, audience, "Audience");
			audiences = Audience.BOTH.equals(code) ? List.of(Audience.BOTH) : List.of(code, Audience.BOTH);
		}
		Pageable all = Pageable.unpaged(Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("id")));
		return faqRepository.search(audiences, categoryId, serviceId, true, null, true, all).stream()
				.map(faqMapper::toFaqDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeFaqResponse> getFaqs(Integer page, Integer size, String audience,
			Long categoryId, Long serviceId, Boolean isPublished, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		List<String> audiences = audience == null || audience.isBlank() ? null
				: List.of(lookupService.validateCode(Types.FAQ_AUDIENCE, audience, "Audience"));
		Page<NexHomeFaq> faqs = faqRepository.search(audiences, categoryId, serviceId, isPublished,
				NexHomePaging.searchText(search), false, pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, faqs, faqs.stream().map(faqMapper::toFaqDto).toList());
	}

	@Override
	@Transactional
	public NexHomeFaqResponse createFaq(NexHomeFaqCreateRequest request) {
		if (request.categoryId() != null && request.serviceId() != null) {
			throw new InvalidRequestException("An FAQ belongs to a category or a service, not both.");
		}
		NexHomeServiceCategory category = request.categoryId() == null ? null
				: categoryRepository.findById(request.categoryId())
						.orElseThrow(() -> new ResourceNotFoundException("Service category does not exist."));
		NexHomeService service = request.serviceId() == null ? null
				: serviceRepository.findById(request.serviceId())
						.orElseThrow(() -> new ResourceNotFoundException("Service does not exist."));
		String audience = lookupService.validateCode(Types.FAQ_AUDIENCE, request.audience(), "Audience");

		NexHomeFaq faq = faqMapper.toFaqEntity(request, category, service, permissionService.currentUserId());
		faq.setAudience(audience);
		faq = faqRepository.save(faq);

		log.info("FAQ {} created", faq.getId());
		return faqMapper.toFaqDto(faq);
	}

	@Override
	@Transactional
	public NexHomeFaqResponse updateFaq(NexHomeFaqUpdateRequest request) {
		NexHomeFaq faq = findFaq(request.faqId());
		String audience = request.audience() == null ? null
				: lookupService.validateCode(Types.FAQ_AUDIENCE, request.audience(), "Audience");

		faqMapper.updateFaqEntity(faq, request, permissionService.currentUserId());
		if (audience != null) {
			faq.setAudience(audience);
		}

		log.info("FAQ {} updated", faq.getId());
		return faqMapper.toFaqDto(faq);
	}

	@Override
	@Transactional
	public void deleteFaq(Long faqId) {
		faqRepository.delete(findFaq(faqId));
		log.info("FAQ {} deleted", faqId);
	}

	private NexHomeFaq findFaq(Long faqId) {
		return faqRepository.findById(faqId).orElseThrow(() -> new ResourceNotFoundException("FAQ does not exist."));
	}
}
