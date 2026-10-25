package com.nexhome.app.dto;

import java.util.List;

/**
 * One page of a list.
 *
 * @param page zero-based page number
 * @param size requested page size
 * @param totalElements matching records on all pages
 */
public record NexHomePageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
