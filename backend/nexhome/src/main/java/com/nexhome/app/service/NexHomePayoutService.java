package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePayoutAdjustRequest;
import com.nexhome.app.dto.NexHomePayoutResponse;
import com.nexhome.app.dto.NexHomePayoutStatsResponse;
import com.nexhome.app.dto.NexHomePayoutStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeTransferResponse;

/**
 * Module 7, money out: partner payouts (owed for completed, paid online bookings, less the service's platform
 * commission) and their transfers (PAYOUT_MANAGE).
 */
public interface NexHomePayoutService {

	/**
	 * A partner's earnings in a period (TODAY, WEEK, MONTH or ALL): the partner themself, or PAYOUT_MANAGE read.
	 */
	com.nexhome.app.dto.NexHomePartnerEarningsResponse getPartnerEarnings(Long partnerProfileId, String period,
			Integer page, Integer size);

	NexHomePageResponse<NexHomePayoutResponse> getPayouts(Integer page, Integer size, String payoutStatus,
			Long partnerProfileId);

	/** The signed-in partner's payouts, newest first. */
	NexHomePageResponse<NexHomePayoutResponse> getMyPayouts(Integer page, Integer size);

	NexHomePayoutStatsResponse getPayoutStats();

	NexHomePayoutResponse updateStatus(NexHomePayoutStatusUpdateRequest request);

	NexHomePayoutResponse adjust(NexHomePayoutAdjustRequest request);

	NexHomePageResponse<NexHomeTransferResponse> getTransfers(Integer page, Integer size);

	/** What NexHome owes a partner (V62), since joining: the partner themself, or PAYOUT_MANAGE read. */
	com.nexhome.app.dto.NexHomePartnerWalletResponse getWallet(Long partnerProfileId);

	/** Records a payment made to a partner outside the app (PAYOUT_MANAGE write). */
	com.nexhome.app.dto.NexHomeManualPayoutResponse recordManualPayout(
			com.nexhome.app.dto.NexHomeManualPayoutCreateRequest request);

	/** Cancels a payout recorded by mistake (PAYOUT_MANAGE write). */
	com.nexhome.app.dto.NexHomeManualPayoutResponse cancelManualPayout(
			com.nexhome.app.dto.NexHomeManualPayoutCancelRequest request);

	/**
	 * Recorded payouts, newest first: one partner's (the partner themself, or PAYOUT_MANAGE read), or every partner's
	 * without {@code partnerProfileId} (PAYOUT_MANAGE read).
	 */
	NexHomePageResponse<com.nexhome.app.dto.NexHomeManualPayoutResponse> getManualPayouts(Long partnerProfileId,
			Integer page, Integer size);

	/** One recorded payout: its partner, or PAYOUT_MANAGE read. */
	com.nexhome.app.dto.NexHomeManualPayoutResponse getManualPayout(Long payoutId);

	/**
	 * What NexHome owes each partner (PAYOUT_MANAGE read): partners with jobs or payouts, the largest balance first;
	 * {@code search} on name, partner id and mobile number; {@code dueOnly} only those still owed something.
	 */
	NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDueResponse> getPartnerDues(Integer page, Integer size,
			String search, Boolean dueOnly);

	com.nexhome.app.dto.NexHomeManualPayoutStatsResponse getManualPayoutStats();

	/** Records a deduction on its own (PAYOUT_MANAGE write; V63). */
	com.nexhome.app.dto.NexHomePartnerDeductionResponse recordDeduction(
			com.nexhome.app.dto.NexHomePartnerDeductionCreateRequest request);

	/** Cancels a deduction recorded by mistake (PAYOUT_MANAGE write). */
	com.nexhome.app.dto.NexHomePartnerDeductionResponse cancelDeduction(
			com.nexhome.app.dto.NexHomePartnerDeductionCancelRequest request);

	/** Deductions, newest first: one partner's (the partner or PAYOUT_MANAGE read), or all (PAYOUT_MANAGE read). */
	NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDeductionResponse> getDeductions(Long partnerProfileId,
			Integer page, Integer size);
}
