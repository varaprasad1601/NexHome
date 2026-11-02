package com.nexhome.app.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeSettingResponse;
import com.nexhome.app.dto.NexHomeSettingsUpdateRequest;
import com.nexhome.app.service.NexHomeSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** General settings (V49): business and contact details, booking rules, staff alerts. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class NexHomeSettingsController {

	private final NexHomeSettingsService settingsService;

	/** Every setting with its definition, value and default. */
	@GetMapping
	@PreAuthorize("@permissions.can('SETTINGS_MANAGE', 'READ')")
	public List<NexHomeSettingResponse> getSettings() {
		return settingsService.getSettings();
	}

	/** The business and contact details as key to value, for the website (public). */
	@GetMapping("/public")
	public Map<String, String> getPublicSettings() {
		return settingsService.getPublicSettings();
	}

	/** Changes the given settings at once (all or none); super users only (V51); returns every setting. */
	@PutMapping("/update")
	@PreAuthorize("@permissions.isCurrentUserSuperUser()")
	public List<NexHomeSettingResponse> updateSettings(@Valid @RequestBody NexHomeSettingsUpdateRequest request) {
		return settingsService.updateSettings(request);
	}
}
