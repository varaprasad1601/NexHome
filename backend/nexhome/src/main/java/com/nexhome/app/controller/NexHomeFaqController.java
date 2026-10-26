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

import com.nexhome.app.dto.NexHomeFaqCreateRequest;
import com.nexhome.app.dto.NexHomeFaqResponse;
import com.nexhome.app.dto.NexHomeFaqUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.service.NexHomeFaqService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * FAQs. Published ones are public ({@code GET /api/faqs}); everything else needs FAQ_MANAGE.
 */
@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class NexHomeFaqController {

	private final NexHomeFaqService faqService;

	/**
	 * Published FAQs in display order. Query parameters (all optional): {@code audience} (CUSTOMER or PARTNER also
	 * return BOTH), {@code categoryId}, {@code serviceId}.
	 */
	@GetMapping
	public List<NexHomeFaqResponse> getPublishedFaqs(@RequestParam(required = false) String audience,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long serviceId) {
		return faqService.getPublishedFaqs(audience, categoryId, serviceId);
	}

	/**
	 * Every FAQ, newest first. Query parameters (all optional): {@code page}, {@code size}, {@code audience} (exact),
	 * {@code categoryId}, {@code serviceId}, {@code isPublished}, {@code search} (part of the question).
	 */
	@GetMapping("/all")
	@PreAuthorize("@permissions.can('FAQ_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeFaqResponse> getFaqs(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String audience,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long serviceId,
			@RequestParam(required = false) Boolean isPublished, @RequestParam(required = false) String search) {
		return faqService.getFaqs(page, size, audience, categoryId, serviceId, isPublished, search);
	}

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('FAQ_MANAGE', 'WRITE')")
	public NexHomeFaqResponse createFaq(@Valid @RequestBody NexHomeFaqCreateRequest request) {
		return faqService.createFaq(request);
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.can('FAQ_MANAGE', 'WRITE')")
	public NexHomeFaqResponse updateFaq(@Valid @RequestBody NexHomeFaqUpdateRequest request) {
		return faqService.updateFaq(request);
	}

	@DeleteMapping("/delete/{faqId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('FAQ_MANAGE', 'DELETE')")
	public void deleteFaq(@PathVariable Long faqId) {
		faqService.deleteFaq(faqId);
	}
}
