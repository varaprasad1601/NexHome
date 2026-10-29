package com.nexhome.app.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePayoutAdjustRequest;
import com.nexhome.app.dto.NexHomePayoutPayRequest;
import com.nexhome.app.dto.NexHomePayoutResponse;
import com.nexhome.app.dto.NexHomePayoutStatsResponse;
import com.nexhome.app.dto.NexHomePayoutStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeTransferResponse;
import com.nexhome.app.service.NexHomePayoutService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 7, money out: partner payouts and transfers (PAYOUT_MANAGE).
 */
@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class NexHomePayoutController {

	private final NexHomePayoutService payoutService;

	@GetMapping
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePayoutResponse> getPayouts(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String payoutStatus,
			@RequestParam(required = false) Long partnerProfileId) {
		return payoutService.getPayouts(page, size, payoutStatus, partnerProfileId);
	}

	@GetMapping("/me")
	public NexHomePageResponse<NexHomePayoutResponse> getMyPayouts(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return payoutService.getMyPayouts(page, size);
	}

	/** The partner themself or PAYOUT_MANAGE read (checked in the service); period TODAY (default), WEEK, MONTH, ALL. */
	@GetMapping("/earnings/{partnerProfileId}")
	public com.nexhome.app.dto.NexHomePartnerEarningsResponse getPartnerEarnings(
			@org.springframework.web.bind.annotation.PathVariable Long partnerProfileId,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String period,
			@org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
			@org.springframework.web.bind.annotation.RequestParam(required = false) Integer size) {
		return payoutService.getPartnerEarnings(partnerProfileId, period, page, size);
	}

	@GetMapping("/stats")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public NexHomePayoutStatsResponse getPayoutStats() {
		return payoutService.getPayoutStats();
	}

	@PutMapping("/pay")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public NexHomePayoutResponse pay(@Valid @RequestBody NexHomePayoutPayRequest request) {
		return payoutService.pay(request);
	}

	@PutMapping("/status/update")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public NexHomePayoutResponse updateStatus(@Valid @RequestBody NexHomePayoutStatusUpdateRequest request) {
		return payoutService.updateStatus(request);
	}

	@PutMapping("/adjust")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public NexHomePayoutResponse adjust(@Valid @RequestBody NexHomePayoutAdjustRequest request) {
		return payoutService.adjust(request);
	}

	@GetMapping("/transfers")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeTransferResponse> getTransfers(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return payoutService.getTransfers(page, size);
	}

}
