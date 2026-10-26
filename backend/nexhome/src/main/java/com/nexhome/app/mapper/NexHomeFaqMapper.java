package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeFaqCreateRequest;
import com.nexhome.app.dto.NexHomeFaqResponse;
import com.nexhome.app.dto.NexHomeFaqUpdateRequest;
import com.nexhome.app.model.NexHomeFaq;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;

@Component
public class NexHomeFaqMapper {

	/** The audience is validated, and the category or service looked up, by the service beforehand. */
	public NexHomeFaq toFaqEntity(NexHomeFaqCreateRequest request, NexHomeServiceCategory category,
			NexHomeService service, Long actingUserId) {
		NexHomeFaq faq = new NexHomeFaq();
		faq.setQuestion(trimToNull(request.question()));
		faq.setAnswer(trimToNull(request.answer()));
		faq.setCategory(category);
		faq.setService(service);
		faq.setAudience(trimToNull(request.audience()));
		faq.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		faq.setPublished(Boolean.TRUE.equals(request.isPublished()));
		faq.setCreatedBy(actingUserId);
		faq.setUpdatedBy(actingUserId);
		return faq;
	}

	/** Applies an update: a null field keeps its current value. The category and service never change. */
	public void updateFaqEntity(NexHomeFaq faq, NexHomeFaqUpdateRequest request, Long actingUserId) {
		if (request.question() != null) {
			faq.setQuestion(trimToNull(request.question()));
		}
		if (request.answer() != null) {
			faq.setAnswer(trimToNull(request.answer()));
		}
		if (request.audience() != null) {
			faq.setAudience(trimToNull(request.audience()));
		}
		if (request.displayOrder() != null) {
			faq.setDisplayOrder(request.displayOrder());
		}
		if (request.isPublished() != null) {
			faq.setPublished(request.isPublished());
		}
		faq.setUpdatedBy(actingUserId);
	}

	public NexHomeFaqResponse toFaqDto(NexHomeFaq faq) {
		return new NexHomeFaqResponse(
				faq.getId(),
				faq.getQuestion(),
				faq.getAnswer(),
				faq.getCategory() == null ? null : faq.getCategory().getId(),
				faq.getService() == null ? null : faq.getService().getId(),
				faq.getAudience(),
				faq.getDisplayOrder(),
				faq.isPublished(),
				faq.getCreationDate());
	}
}
