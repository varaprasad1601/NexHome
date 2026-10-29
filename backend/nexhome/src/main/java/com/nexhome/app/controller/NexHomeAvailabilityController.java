package com.nexhome.app.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeAvailablePartnerResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffCreateRequest;
import com.nexhome.app.dto.NexHomePartnerTimeOffResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffUpdateRequest;
import com.nexhome.app.service.NexHomeAvailabilityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 5, availability and slots: partners' time off (the partner or PARTNER_AVAILABILITY_MANAGE, checked in the
 * service) and finding free partners (staff). Slot holds and reservations are not used for now. Times in requests
 * are ISO-8601 instants.
 */
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class NexHomeAvailabilityController {

	private final NexHomeAvailabilityService availabilityService;

	/** The partner or PARTNER_AVAILABILITY_MANAGE read; {@code upcoming=true}: only what is not over yet. */
	@GetMapping("/time-off/{partnerProfileId}")
	public List<NexHomePartnerTimeOffResponse> getTimeOff(@PathVariable Long partnerProfileId,
			@RequestParam(defaultValue = "false") boolean upcoming) {
		return availabilityService.getTimeOff(partnerProfileId, upcoming);
	}

	@PostMapping("/time-off/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePartnerTimeOffResponse addTimeOff(@Valid @RequestBody NexHomePartnerTimeOffCreateRequest request) {
		return availabilityService.addTimeOff(request);
	}

	@PutMapping("/time-off/update")
	public NexHomePartnerTimeOffResponse updateTimeOff(
			@Valid @RequestBody NexHomePartnerTimeOffUpdateRequest request) {
		return availabilityService.updateTimeOff(request);
	}

	/** Time off is cancelled, never deleted: the partner or PARTNER_AVAILABILITY_MANAGE write (checked in the service). */
	@PutMapping("/time-off/cancel")
	public NexHomePartnerTimeOffResponse cancelTimeOff(
			@Valid @RequestBody com.nexhome.app.dto.NexHomePartnerTimeOffCancelRequest request) {
		return availabilityService.cancelTimeOff(request);
	}

	/** Partners free for {@code serviceId} during [startsAt, endsAt); {@code includeBusy=true}: the busy ones too. */
	@GetMapping("/partners")
	@PreAuthorize("@permissions.can('PARTNER_AVAILABILITY_MANAGE', 'READ')")
	public List<NexHomeAvailablePartnerResponse> findAvailablePartners(@RequestParam Long serviceId,
			@RequestParam Instant startsAt, @RequestParam Instant endsAt,
			@RequestParam(defaultValue = "false") boolean includeBusy) {
		return availabilityService.findAvailablePartners(serviceId, startsAt, endsAt, includeBusy);
	}
}
