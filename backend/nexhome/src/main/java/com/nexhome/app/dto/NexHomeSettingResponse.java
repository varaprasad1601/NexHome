package com.nexhome.app.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One general setting with what the console needs to edit it: its group (BUSINESS, BOOKING, STAFF_ALERTS), type
 * (TEXT, PHONE, EMAIL, NUMBER, DECIMAL, ALERT), label and help, the current value and the default, the limits
 * (NUMBER / DECIMAL: min and max; TEXT: max length) and, for ALERT, the options. {@code isPublic} settings are also
 * served without a token ({@code GET /api/settings/public}). {@code updatedBy} / {@code updatedDate} are null while
 * the default applies.
 */
public record NexHomeSettingResponse(
		String settingKey,
		String settingGroup,
		String valueType,
		String label,
		String description,
		String value,
		String defaultValue,
		Double min,
		Double max,
		Integer maxLength,
		List<String> options,
		boolean isPublic,
		Long updatedBy,
		String updatedByName,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant updatedDate) {
}
