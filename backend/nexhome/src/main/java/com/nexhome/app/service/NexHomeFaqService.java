package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeFaqCreateRequest;
import com.nexhome.app.dto.NexHomeFaqResponse;
import com.nexhome.app.dto.NexHomeFaqUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;

/**
 * FAQs for customers and partners: general, per category or per service. Published FAQs are public; maintaining
 * them needs FAQ_MANAGE.
 */
public interface NexHomeFaqService {

	/**
	 * Published FAQs in display order, leaving out those of an inactive category or service.
	 *
	 * @param audience optional FAQ_AUDIENCE code; CUSTOMER or PARTNER also include BOTH
	 * @param categoryId optional: only FAQs of this category
	 * @param serviceId optional: only FAQs of this service
	 */
	List<NexHomeFaqResponse> getPublishedFaqs(String audience, Long categoryId, Long serviceId);

	/** One page of all FAQs, published or not, newest first; the audience filter is exact. */
	NexHomePageResponse<NexHomeFaqResponse> getFaqs(Integer page, Integer size, String audience, Long categoryId,
			Long serviceId, Boolean isPublished, String search);

	NexHomeFaqResponse createFaq(NexHomeFaqCreateRequest request);

	NexHomeFaqResponse updateFaq(NexHomeFaqUpdateRequest request);

	void deleteFaq(Long faqId);
}
