package com.nexhome.app.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Changes some general settings at once (all or none): each {@code settingKey} with its new {@code value} as text,
 * checked against the setting's type and limits.
 */
public record NexHomeSettingsUpdateRequest(
		@NotEmpty(message = "Settings are required")
		@Size(max = 50, message = "At most 50 settings can be changed at once")
		List<@Valid @NotNull(message = "Settings cannot contain null") Change> settings) {

	public record Change(
			@NotBlank(message = "Setting key is required")
			String settingKey,

			@NotNull(message = "Setting value is required")
			@Size(max = 2000, message = "Setting value must be at most 2000 characters")
			String value) {
	}
}
