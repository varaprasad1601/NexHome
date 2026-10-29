package com.nexhome.app.dto;

import java.util.List;
import java.util.Map;

/** The entity types logged so far, and the actions of each, for the console's filters. */
public record NexHomeAuditLogFiltersResponse(
		List<String> entityTypes,
		Map<String, List<String>> actionsByEntityType) {
}
