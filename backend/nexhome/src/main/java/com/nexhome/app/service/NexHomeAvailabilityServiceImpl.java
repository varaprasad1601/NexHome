package com.nexhome.app.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeAvailablePartnerResponse;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffCreateRequest;
import com.nexhome.app.dto.NexHomePartnerTimeOffResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAvailabilityMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerOnboardingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomePartnerService;
import com.nexhome.app.model.NexHomePartnerTimeOff;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomePartnerTimeOffRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeAvailabilityServiceImpl implements NexHomeAvailabilityService {

	static final String PARTNER_AVAILABILITY_MANAGE = "PARTNER_AVAILABILITY_MANAGE";
	/** Weekly slots and bookings are in Indian time. */
	static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	private static final Duration LONGEST_SLOT = Duration.ofHours(12);
	/** Bookings that keep their partner busy: given to the partner and not finished. */
	private static final List<String> BUSY_STATUSES = List.of(BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE,
			BookingStatus.IN_PROGRESS, BookingStatus.PAUSED);

	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePartnerServiceRepository partnerServiceRepository;
	private final NexHomePartnerTimeOffRepository timeOffRepository;
	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAvailabilityMapper availabilityMapper;
	private final NexHomePartnerProfileMapper partnerProfileMapper;
	private final NexHomeAuditRecorder auditRecorder;

	// ------------------------------------------------------------------------------------------------------------
	// Time off
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePartnerTimeOffResponse> getTimeOff(Long partnerProfileId, boolean upcoming) {
		NexHomePartnerProfile partner = findPartner(partnerProfileId);
		requireSelfOrCan(partner, NexHomeAccess.READ);
		return (upcoming ? timeOffRepository.findByPartnerIdAndEndsAtAfterOrderByStartsAtAsc(partnerProfileId,
				Instant.now()) : timeOffRepository.findByPartnerIdOrderByStartsAtDesc(partnerProfileId)).stream()
				.map(availabilityMapper::toTimeOffDto)
				.toList();
	}

	@Override
	@Transactional
	public NexHomePartnerTimeOffResponse addTimeOff(NexHomePartnerTimeOffCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = findPartner(request.partnerProfileId());
		requireSelfOrCan(partner, NexHomeAccess.WRITE);
		checkTimeOff(partner.getId(), request.startsAt(), request.endsAt(), -1L);

		NexHomePartnerTimeOff timeOff = new NexHomePartnerTimeOff();
		timeOff.setPartner(partner);
		timeOff.setStartsAt(request.startsAt());
		timeOff.setEndsAt(request.endsAt());
		timeOff.setReason(NexHomeText.trimToNull(request.reason()));
		timeOff.setCreatedBy(actingUserId);
		timeOff.setUpdatedBy(actingUserId);
		timeOff = timeOffRepository.save(timeOff);

		log.info("Time off {} added for partner {}", timeOff.getId(), partner.getPublicId());
		return availabilityMapper.toTimeOffDto(timeOff);
	}

	@Override
	@Transactional
	public NexHomePartnerTimeOffResponse updateTimeOff(NexHomePartnerTimeOffUpdateRequest request) {
		NexHomePartnerTimeOff timeOff = findTimeOff(request.timeOffId());
		requireSelfOrCan(timeOff.getPartner(), NexHomeAccess.WRITE);
		if (timeOff.getCancelledAt() != null) {
			throw new InvalidRequestException("Cancelled time off cannot be changed.");
		}
		Instant startsAt = request.startsAt() != null ? request.startsAt() : timeOff.getStartsAt();
		Instant endsAt = request.endsAt() != null ? request.endsAt() : timeOff.getEndsAt();
		// Checks query first, so nothing is changed before they pass
		checkTimeOff(timeOff.getPartner().getId(), startsAt, endsAt, timeOff.getId());

		timeOff.setStartsAt(startsAt);
		timeOff.setEndsAt(endsAt);
		if (request.reason() != null) {
			timeOff.setReason(NexHomeText.trimToNull(request.reason()));
		}
		timeOff.setUpdatedBy(permissionService.currentUserId());

		log.info("Time off {} updated", timeOff.getId());
		return availabilityMapper.toTimeOffDto(timeOff);
	}

	@Override
	@Transactional
	public NexHomePartnerTimeOffResponse cancelTimeOff(com.nexhome.app.dto.NexHomePartnerTimeOffCancelRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerTimeOff timeOff = findTimeOff(request.timeOffId());
		requireSelfOrCan(timeOff.getPartner(), NexHomeAccess.WRITE);
		if (timeOff.getCancelledAt() != null) {
			throw new InvalidRequestException("The time off is already cancelled.");
		}
		Instant now = Instant.now();
		if (!timeOff.getEndsAt().isAfter(now)) {
			throw new InvalidRequestException("Time off that is over cannot be cancelled.");
		}
		// Kept, not deleted: leave under way ends now (the days taken stay on record), upcoming leave is cancelled
		boolean underWay = !timeOff.getStartsAt().isAfter(now);
		if (underWay) {
			timeOff.setEndsAt(now);
		}
		timeOff.setCancelledAt(now);
		timeOff.setCancelledBy(actingUserId);
		timeOff.setCancellationReason(NexHomeText.trimToNull(request.reason()));
		timeOff.setUpdatedBy(actingUserId);
		auditRecorder.record(underWay ? "TIME_OFF_ENDED_EARLY" : "TIME_OFF_CANCELLED", "PARTNER_PROFILE",
				timeOff.getPartner().getPublicId(), null, NexHomeAuditRecorder.values("timeOffId", timeOff.getId(),
						"reason", timeOff.getCancellationReason()), actingUserId);
		log.info("Time off {} {} by User ID {}", timeOff.getId(), underWay ? "ended early" : "cancelled", actingUserId);
		return availabilityMapper.toTimeOffDto(timeOff);
	}

	/** A period of time off: a real range, not over already, not overlapping other time off or the partner's jobs. */
	private void checkTimeOff(Long partnerId, Instant startsAt, Instant endsAt, Long excludeId) {
		if (!endsAt.isAfter(startsAt)) {
			throw new InvalidRequestException("End must be after the start.");
		}
		if (!endsAt.isAfter(Instant.now())) {
			throw new InvalidRequestException("Time off cannot end in the past.");
		}
		if (timeOffRepository.existsOverlapping(partnerId, startsAt, endsAt, excludeId)) {
			throw new DuplicateResourceException("The partner already has time off in this period.");
		}
		if (bookingRepository.existsPartnerBookedBetween(partnerId, startsAt, endsAt, BUSY_STATUSES, -1L)) {
			throw new DuplicateResourceException(
					"The partner has bookings in this period; unassign them first.");
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Free partners
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public void requireFree(Long partnerProfileId, Long serviceId, Instant startsAt, Instant endsAt,
			boolean outsideWeeklyHours, Long bookingId) {
		NexHomePartnerProfile partner = findPartner(partnerProfileId);
		checkSlotRange(startsAt, endsAt);
		requireBookable(partner);
		if (serviceId != null && partnerServiceRepository
				.findByPartnerIdOrderByPrimaryServiceDescCreationDateDescIdDesc(partner.getId()).stream()
				.noneMatch(offered -> offered.isActive() && offered.getService().getId().equals(serviceId))) {
			throw new InvalidRequestException("The partner does not offer this service.");
		}
		if (!outsideWeeklyHours && !worksDuring(partner, startsAt, endsAt, slotTimes())) {
			throw new InvalidRequestException("The partner does not work at that time (weekly availability).");
		}
		if (timeOffRepository.existsOverlapping(partner.getId(), startsAt, endsAt, -1L)) {
			throw new DuplicateResourceException("The partner is on time off then.");
		}
		if (bookingRepository.existsPartnerBookedBetween(partner.getId(), startsAt, endsAt, BUSY_STATUSES,
				bookingId == null ? -1L : bookingId)) {
			throw new DuplicateResourceException("The partner already has a booking then.");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAvailablePartnerResponse> findAvailablePartners(Long serviceId, Instant startsAt,
			Instant endsAt, boolean includeBusy) {
		checkSlotRange(startsAt, endsAt);
		List<NexHomePartnerService> offering = partnerServiceRepository.findByServiceIdAndActiveTrue(serviceId).stream()
				.filter(offered -> isBookable(offered.getPartner()))
				.toList();
		if (offering.isEmpty()) {
			return List.of();
		}
		List<Long> partnerIds = offering.stream().map(offered -> offered.getPartner().getId()).toList();
		Set<Long> off = new HashSet<>(timeOffRepository.findPartnersOffBetween(partnerIds, startsAt, endsAt));
		Set<Long> booked = new HashSet<>(bookingRepository.findPartnersBookedBetween(partnerIds, startsAt, endsAt,
				BUSY_STATUSES));
		List<SlotTime> slots = slotTimes();
		return offering.stream()
				.map(offered -> {
					NexHomePartnerProfile partner = offered.getPartner();
					String busyReason = booked.contains(partner.getId()) ? "BOOKED"
							: off.contains(partner.getId()) ? "TIME_OFF"
									: worksDuring(partner, startsAt, endsAt, slots) ? null : "OUTSIDE_WEEKLY_HOURS";
					return new NexHomeAvailablePartnerResponse(partner.getId(), partner.getPublicId(),
							partner.getUser().getId(), partner.getFirstName(), partner.getLastName(),
							partner.getCity(), partner.getAvailabilityStatus(), offered.getExperienceYears(),
							offered.isPrimaryService(), busyReason == null, busyReason);
				})
				.filter(partner -> includeBusy || partner.isFree())
				// Free first, then main-service partners, then the most experienced
				.sorted(Comparator.comparing((NexHomeAvailablePartnerResponse partner) -> !partner.isFree())
						.thenComparing(partner -> !partner.isPrimaryService())
						.thenComparing(partner -> partner.experienceYears() == null ? 0 : -partner.experienceYears()))
				.toList();
	}


	// ------------------------------------------------------------------------------------------------------------
	// Rules
	// ------------------------------------------------------------------------------------------------------------

	/** A slot: a real range in the future, at most 12 hours. */
	private static void checkSlotRange(Instant startsAt, Instant endsAt) {
		if (startsAt == null || endsAt == null) {
			throw new InvalidRequestException("Start and end are required.");
		}
		if (!endsAt.isAfter(startsAt)) {
			throw new InvalidRequestException("End must be after the start.");
		}
		if (!startsAt.isAfter(Instant.now())) {
			throw new InvalidRequestException("A slot must start in the future.");
		}
		if (Duration.between(startsAt, endsAt).compareTo(LONGEST_SLOT) > 0) {
			throw new InvalidRequestException("A slot can be at most 12 hours long.");
		}
	}

	/** Only an approved partner with an active account takes work. */
	private static void requireBookable(NexHomePartnerProfile partner) {
		if (!PartnerOnboardingStatus.APPROVED.equals(partner.getOnboardingStatus())) {
			throw new InvalidRequestException("Only an APPROVED partner can be booked.");
		}
		if (!AccountStatus.ACTIVE.equals(partner.getUser().getAccountStatus())) {
			throw new InvalidRequestException("The partner's account is not active.");
		}
	}

	private static boolean isBookable(NexHomePartnerProfile partner) {
		return PartnerOnboardingStatus.APPROVED.equals(partner.getOnboardingStatus())
				&& AccountStatus.ACTIVE.equals(partner.getUser().getAccountStatus());
	}

	/** A daily slot of the weekly grid with its times (AVAILABILITY_SLOT metadata start / end, "HH:mm"). */
	record SlotTime(String code, LocalTime start, LocalTime end) {
	}

	private List<SlotTime> slotTimes() {
		List<SlotTime> slots = new ArrayList<>();
		for (NexHomeLookupValueResponse value : lookupService.getActiveLookupValues(Types.AVAILABILITY_SLOT)) {
			Object start = value.metadata() == null ? null : value.metadata().get("start");
			Object end = value.metadata() == null ? null : value.metadata().get("end");
			if (start != null && end != null) {
				try {
					slots.add(new SlotTime(value.valueCode(), LocalTime.parse(start.toString()),
							LocalTime.parse(end.toString())));
				} catch (DateTimeParseException e) {
					log.warn("AVAILABILITY_SLOT {} has unreadable times {} - {}", value.valueCode(), start, end);
				}
			}
		}
		return slots;
	}

	/**
	 * Whether [startsAt, endsAt) lies within the partner's weekly slots of that day, in Indian time: inside one slot or
	 * slots that follow each other without a gap (the lunch break is a gap).
	 */
	boolean worksDuring(NexHomePartnerProfile partner, Instant startsAt, Instant endsAt, List<SlotTime> slots) {
		ZonedDateTime start = startsAt.atZone(ZONE);
		ZonedDateTime end = endsAt.atZone(ZONE);
		boolean endsAtMidnight = end.toLocalTime().equals(LocalTime.MIDNIGHT)
				&& end.toLocalDate().equals(start.toLocalDate().plusDays(1));
		if (!endsAtMidnight && !end.toLocalDate().equals(start.toLocalDate())) {
			return false;
		}
		Set<String> chosen = new HashSet<>(partnerProfileMapper.readAvailability(partner.getWeeklyAvailability())
				.getOrDefault(start.getDayOfWeek().name(), List.of()));
		List<SlotTime> day = slots.stream()
				.filter(slot -> chosen.contains(slot.code()))
				.sorted(Comparator.comparing(SlotTime::start))
				.toList();
		LocalTime from = start.toLocalTime();
		LocalTime to = endsAtMidnight ? LocalTime.MAX : end.toLocalTime();
		// From the slot the window starts in, join the slots that follow without a gap until the window is covered
		LocalTime coveredUntil = null;
		for (SlotTime slot : day) {
			if (coveredUntil == null) {
				if (slot.start().isAfter(from) || !slot.end().isAfter(from)) {
					continue;
				}
				coveredUntil = slot.end();
			} else if (slot.start().isAfter(coveredUntil)) {
				return false;
			} else if (slot.end().isAfter(coveredUntil)) {
				coveredUntil = slot.end();
			}
			if (!coveredUntil.isBefore(to)) {
				return true;
			}
		}
		return false;
	}

	private void requireSelfOrCan(NexHomePartnerProfile partner, NexHomeAccess access) {
		if (!permissionService.isSelfOrCan(partner.getUser().getId(), PARTNER_AVAILABILITY_MANAGE, access.name())) {
			throw new AccessDeniedException("Not the partner and no " + PARTNER_AVAILABILITY_MANAGE + " " + access
					+ " access.");
		}
	}

	private NexHomePartnerProfile findPartner(Long partnerProfileId) {
		return partnerProfileRepository.findById(partnerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
	}

	private NexHomePartnerTimeOff findTimeOff(Long timeOffId) {
		return timeOffRepository.findById(timeOffId)
				.orElseThrow(() -> new ResourceNotFoundException("Time off does not exist."));
	}
}
