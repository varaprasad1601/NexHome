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

	/** The partner themself or PAYOUT_MANAGE read (checked in the service). */
	@GetMapping("/wallet/{partnerProfileId}")
	public com.nexhome.app.dto.NexHomePartnerWalletResponse getWallet(
			@org.springframework.web.bind.annotation.PathVariable Long partnerProfileId) {
		return payoutService.getWallet(partnerProfileId);
	}

	@org.springframework.web.bind.annotation.PostMapping("/manual/create")
	@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public com.nexhome.app.dto.NexHomeManualPayoutResponse recordManualPayout(
			@Valid @RequestBody com.nexhome.app.dto.NexHomeManualPayoutCreateRequest request) {
		return payoutService.recordManualPayout(request);
	}

	@PutMapping("/manual/cancel")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public com.nexhome.app.dto.NexHomeManualPayoutResponse cancelManualPayout(
			@Valid @RequestBody com.nexhome.app.dto.NexHomeManualPayoutCancelRequest request) {
		return payoutService.cancelManualPayout(request);
	}

	/** One partner's (the partner themself or PAYOUT_MANAGE read), or every partner's (PAYOUT_MANAGE read). */
	@GetMapping("/manual")
	public NexHomePageResponse<com.nexhome.app.dto.NexHomeManualPayoutResponse> getManualPayouts(
			@RequestParam(required = false) Long partnerProfileId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return payoutService.getManualPayouts(partnerProfileId, page, size);
	}

	@GetMapping("/manual/stats")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public com.nexhome.app.dto.NexHomeManualPayoutStatsResponse getManualPayoutStats() {
		return payoutService.getManualPayoutStats();
	}

	/** Its partner, or PAYOUT_MANAGE read (checked in the service). */
	@GetMapping("/manual/{payoutId}")
	public com.nexhome.app.dto.NexHomeManualPayoutResponse getManualPayout(
			@org.springframework.web.bind.annotation.PathVariable Long payoutId) {
		return payoutService.getManualPayout(payoutId);
	}

	@org.springframework.web.bind.annotation.PostMapping("/deductions/create")
	@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public com.nexhome.app.dto.NexHomePartnerDeductionResponse recordDeduction(
			@Valid @RequestBody com.nexhome.app.dto.NexHomePartnerDeductionCreateRequest request) {
		return payoutService.recordDeduction(request);
	}

	@PutMapping("/deductions/cancel")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'WRITE')")
	public com.nexhome.app.dto.NexHomePartnerDeductionResponse cancelDeduction(
			@Valid @RequestBody com.nexhome.app.dto.NexHomePartnerDeductionCancelRequest request) {
		return payoutService.cancelDeduction(request);
	}

	/** One partner's (the partner themself or PAYOUT_MANAGE read), or every partner's (PAYOUT_MANAGE read). */
	@GetMapping("/deductions")
	public NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDeductionResponse> getDeductions(
			@RequestParam(required = false) Long partnerProfileId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return payoutService.getDeductions(partnerProfileId, page, size);
	}

	@GetMapping("/dues")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDueResponse> getPartnerDues(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String search, @RequestParam(required = false) Boolean dueOnly) {
		return payoutService.getPartnerDues(page, size, search, dueOnly);
	}

	@GetMapping("/transfers")
	@PreAuthorize("@permissions.can('PAYOUT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeTransferResponse> getTransfers(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return payoutService.getTransfers(page, size);
	}

}
