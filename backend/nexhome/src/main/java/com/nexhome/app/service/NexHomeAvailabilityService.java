package com.nexhome.app.service;

import java.time.Instant;
import java.util.List;

import com.nexhome.app.dto.NexHomeAvailablePartnerResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffCreateRequest;
import com.nexhome.app.dto.NexHomePartnerTimeOffResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffUpdateRequest;

/**
 * Module 5: when partners can work. Weekly hours are the partner profile's slot grid; this service adds time off and
 * finding the partners free for a service at a time. A partner is busy when they hold another booking then (slot
 * holds and reservations are not used for now; the table is kept).
 */
public interface NexHomeAvailabilityService {

	/** The partner's time off, latest first; {@code upcoming} only what is not over yet (soonest first). */
	List<NexHomePartnerTimeOffResponse> getTimeOff(Long partnerProfileId, boolean upcoming);

	NexHomePartnerTimeOffResponse addTimeOff(NexHomePartnerTimeOffCreateRequest request);

	NexHomePartnerTimeOffResponse updateTimeOff(NexHomePartnerTimeOffUpdateRequest request);

	/** The partner or PARTNER_AVAILABILITY_MANAGE write: upcoming time off is cancelled, time off under way ends now. */
	NexHomePartnerTimeOffResponse cancelTimeOff(com.nexhome.app.dto.NexHomePartnerTimeOffCancelRequest request);

	/**
	 * Partners free for the service during [startsAt, endsAt); with {@code includeBusy} every bookable partner offering
	 * it, the busy ones last with the reason.
	 */
	List<NexHomeAvailablePartnerResponse> findAvailablePartners(Long serviceId, Instant startsAt, Instant endsAt,
			boolean includeBusy);

	/**
	 * For bookings (Module 6): the partner can take the booking {@code bookingId} during [startsAt, endsAt): approved
	 * with an active account, offering the service, working then (unless {@code outsideWeeklyHours}), not on time off
	 * and without another booking then (400 / 409 otherwise).
	 */
	void requireFree(Long partnerProfileId, Long serviceId, Instant startsAt, Instant endsAt,
			boolean outsideWeeklyHours, Long bookingId);
}
