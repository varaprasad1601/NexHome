package com.nexhome.app.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.InvalidRequestException;

/**
 * Paging rules shared by the list endpoints: {@code page} is zero-based (default 0), {@code size} is 1 to 100
 * (default 10), and records come newest first.
 */
final class NexHomePaging {

	static final int DEFAULT_PAGE_SIZE = 10;
	static final int MAX_PAGE_SIZE = 100;

	private NexHomePaging() {
	}

	/** Newest first: {@code creationDate} desc, then {@code id} desc. */
	static PageRequest newestFirst(Integer page, Integer size) {
		int pageNumber = page == null ? 0 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
		if (pageNumber < 0) {
			throw new InvalidRequestException("Page must be 0 or more.");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new InvalidRequestException("Size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.desc("creationDate"), Sort.Order.desc("id")));
	}

	/** The requested page number and size, with the totals of the result. */
	static <T> NexHomePageResponse<T> toPageResponse(PageRequest request, Page<?> page, List<T> content) {
		return new NexHomePageResponse<>(content, request.getPageNumber(), request.getPageSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	/** Optional text filters: trimmed and lower-cased, or null when empty. */
	static String searchText(String search) {
		return search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
	}
}
