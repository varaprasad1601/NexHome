package com.nexhome.app.dto;

import java.util.List;

/**
 * A service with its variants, media and requirements, in display order. Public callers only see active variants and
 * media.
 */
public record NexHomeServiceDetailResponse(
		NexHomeServiceResponse service,
		List<NexHomeServiceVariantResponse> variants,
		List<NexHomeServiceMediaResponse> media,
		List<NexHomeServiceRequirementResponse> requirements) {
}
