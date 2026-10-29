package com.nexhome.app.dto;

import java.util.List;

/**
 * A service with its images, variants, media, requirements and, for CATALOG_MANAGE readers, its activity log, in display order. Public callers only see active variants and
 * media.
 */
public record NexHomeServiceDetailResponse(
		NexHomeServiceResponse service,

		List<NexHomeServiceImageResponse> images,
		List<NexHomeServiceVariantResponse> variants,
		List<NexHomeServiceMediaResponse> media,
		List<NexHomeServiceRequirementResponse> requirements,

		// Newest first; null for callers without CATALOG_MANAGE read
		List<NexHomeServiceActivityResponse> activity) {
}
