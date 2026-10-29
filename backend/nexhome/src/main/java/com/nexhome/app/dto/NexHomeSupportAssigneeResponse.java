package com.nexhome.app.dto;

/** A staff member a ticket can be assigned to: active staff who can read support tickets. */
public record NexHomeSupportAssigneeResponse(
		Long userId,
		String name,
		String role) {
}
