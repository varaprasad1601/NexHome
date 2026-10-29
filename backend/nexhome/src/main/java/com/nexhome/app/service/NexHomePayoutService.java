package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePayoutAdjustRequest;
import com.nexhome.app.dto.NexHomePayoutPayRequest;
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

	/** Sends the payout to the partner's verified primary bank account. */
	NexHomePayoutResponse pay(NexHomePayoutPayRequest request);

	NexHomePayoutResponse updateStatus(NexHomePayoutStatusUpdateRequest request);

	NexHomePayoutResponse adjust(NexHomePayoutAdjustRequest request);

	NexHomePageResponse<NexHomeTransferResponse> getTransfers(Integer page, Integer size);
}
