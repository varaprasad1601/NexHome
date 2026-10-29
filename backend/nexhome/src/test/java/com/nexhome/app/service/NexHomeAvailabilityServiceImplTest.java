package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.config.NexHomeFieldEncryptor;
import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomePartnerTimeOffCreateRequest;
import com.nexhome.app.dto.NexHomePartnerTimeOffUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.mapper.NexHomeAvailabilityMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomePartnerService;
import com.nexhome.app.model.NexHomePartnerTimeOff;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomePartnerTimeOffRepository;

import tools.jackson.databind.json.JsonMapper;

class NexHomeAvailabilityServiceImplTest {

	private static final Long PARTNER_ID = 30L;
	private static final Long OWNER_ID = 20L;
	private static final Long STAFF_ID = 1L;
	private static final Long SERVICE_ID = 7L;

	private NexHomePartnerProfileRepository partnerProfileRepository;
	private NexHomePartnerServiceRepository partnerServiceRepository;
	private NexHomePartnerTimeOffRepository timeOffRepository;
	private NexHomeBookingRepository bookingRepository;
	private NexHomePermissionService permissionService;
	private NexHomeAvailabilityServiceImpl availabilityService;
	private NexHomePartnerProfile partner;

	@BeforeEach
	void setUp() {
		partnerProfileRepository = mock(NexHomePartnerProfileRepository.class);
		partnerServiceRepository = mock(NexHomePartnerServiceRepository.class);
		timeOffRepository = mock(NexHomePartnerTimeOffRepository.class);
		bookingRepository = mock(NexHomeBookingRepository.class);
		permissionService = mock(NexHomePermissionService.class);
		NexHomeLookupService lookupService = mock(NexHomeLookupService.class);
		when(lookupService.validateCode(any(), any(), any()))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		// The V22 slots: a lunch break from 12 to 1
		when(lookupService.getActiveLookupValues("AVAILABILITY_SLOT")).thenReturn(List.of(
				slot("MORNING", "09:00", "12:00"), slot("AFTERNOON", "13:00", "15:00"),
				slot("EVENING", "15:00", "18:00")));
		when(permissionService.currentUserId()).thenReturn(STAFF_ID);
		when(permissionService.isSelfOrCan(any(), any(), any())).thenReturn(true);
		when(timeOffRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		NexHomeFieldEncryptor encryptor = new NexHomeFieldEncryptor(new NexHomeSecurityProperties("unused", "nexhome",
				15, 30, List.of(), Base64.getEncoder().encodeToString(new byte[32])));
		availabilityService = new NexHomeAvailabilityServiceImpl(partnerProfileRepository, partnerServiceRepository,
				timeOffRepository, bookingRepository, lookupService, permissionService,
				new NexHomeAvailabilityMapper(), new NexHomePartnerProfileMapper(JsonMapper.builder().build(), encryptor),
				mock(NexHomeAuditRecorder.class));

		NexHomeUser owner = new NexHomeUser();
		owner.setId(OWNER_ID);
		owner.setAccountStatus("ACTIVE");
		partner = new NexHomePartnerProfile();
		partner.setId(PARTNER_ID);
		partner.setPublicId("NHPART0030");
		partner.setUser(owner);
		partner.setOnboardingStatus("APPROVED");
		// Mondays: all three slots; Tuesdays: the morning only
		partner.setWeeklyAvailability(
				"{\"MONDAY\":[\"MORNING\",\"AFTERNOON\",\"EVENING\"],\"TUESDAY\":[\"MORNING\"]}");
		when(partnerProfileRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
	}

	// ------------------------------------------------------------------------------------------------------------
	// Weekly slots
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void aSlotMustLieWithinTheDaysSlotsWithoutCrossingTheLunchBreak() {
		LocalDate monday = nextMonday();
		List<NexHomeAvailabilityServiceImpl.SlotTime> slots = List.of(
				new NexHomeAvailabilityServiceImpl.SlotTime("MORNING", LocalTime.of(9, 0), LocalTime.of(12, 0)),
				new NexHomeAvailabilityServiceImpl.SlotTime("AFTERNOON", LocalTime.of(13, 0), LocalTime.of(15, 0)),
				new NexHomeAvailabilityServiceImpl.SlotTime("EVENING", LocalTime.of(15, 0), LocalTime.of(18, 0)));

		assertThat(availabilityService.worksDuring(partner, at(monday, 10, 0), at(monday, 12, 0), slots)).isTrue();
		// Afternoon and evening follow each other
		assertThat(availabilityService.worksDuring(partner, at(monday, 14, 0), at(monday, 17, 0), slots)).isTrue();
		// The lunch break is a gap
		assertThat(availabilityService.worksDuring(partner, at(monday, 11, 0), at(monday, 13, 30), slots)).isFalse();
		assertThat(availabilityService.worksDuring(partner, at(monday, 8, 0), at(monday, 10, 0), slots)).isFalse();
		assertThat(availabilityService.worksDuring(partner, at(monday, 17, 0), at(monday, 19, 0), slots)).isFalse();
		// Tuesday: the morning only
		LocalDate tuesday = monday.plusDays(1);
		assertThat(availabilityService.worksDuring(partner, at(tuesday, 9, 0), at(tuesday, 11, 0), slots)).isTrue();
		assertThat(availabilityService.worksDuring(partner, at(tuesday, 14, 0), at(tuesday, 15, 0), slots)).isFalse();
		// Wednesday: not working
		LocalDate wednesday = monday.plusDays(2);
		assertThat(availabilityService.worksDuring(partner, at(wednesday, 9, 0), at(wednesday, 10, 0), slots))
				.isFalse();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Taking a booking (no slot is held)
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void aPartnerTakesABookingWhenApprovedWorkingAndFree() {
		LocalDate monday = nextMonday();
		Instant start = at(monday, 9, 30);
		Instant end = at(monday, 11, 30);

		availabilityService.requireFree(PARTNER_ID, null, start, end, false, 12L);
		// The booking itself does not count against its partner
		verify(bookingRepository).existsPartnerBookedBetween(eq(PARTNER_ID), eq(start), eq(end), any(), eq(12L));

		when(timeOffRepository.existsOverlapping(eq(PARTNER_ID), any(), any(), eq(-1L))).thenReturn(true);
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, start, end, false, 12L))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("The partner is on time off then.");

		when(timeOffRepository.existsOverlapping(eq(PARTNER_ID), any(), any(), eq(-1L))).thenReturn(false);
		when(bookingRepository.existsPartnerBookedBetween(eq(PARTNER_ID), any(), any(), any(), any()))
				.thenReturn(true);
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, start, end, false, 12L))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("The partner already has a booking then.");
		when(bookingRepository.existsPartnerBookedBetween(eq(PARTNER_ID), any(), any(), any(), any()))
				.thenReturn(false);

		// Outside the weekly hours only when staff allow it; the lunch break is a gap
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, at(monday, 11, 0),
				at(monday, 13, 30), false, 12L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The partner does not work at that time (weekly availability).");
		availabilityService.requireFree(PARTNER_ID, null, at(monday, 11, 0), at(monday, 13, 30), true, 12L);

		partner.setOnboardingStatus("UNDER_REVIEW");
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, start, end, false, 12L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Only an APPROVED partner can be booked.");
	}

	@Test
	void aBookingIsForAServiceThePartnerOffersAndAFutureRange() {
		LocalDate monday = nextMonday();
		when(partnerServiceRepository.findByPartnerIdOrderByPrimaryServiceDescCreationDateDescIdDesc(PARTNER_ID))
				.thenReturn(List.of(offering(8L, true)));

		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, SERVICE_ID, at(monday, 9, 30),
				at(monday, 10, 30), false, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The partner does not offer this service.");
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, at(monday, 10, 30),
				at(monday, 9, 30), false, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("End must be after the start.");
		assertThatThrownBy(() -> availabilityService.requireFree(PARTNER_ID, null, Instant.now().minusSeconds(60),
				Instant.now().plusSeconds(3600), false, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A slot must start in the future.");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Time off
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void timeOffIsARealFuturePeriodClearOfOtherTimeOffAndBookings() {
		Instant start = Instant.now().plus(Duration.ofDays(2));
		Instant end = start.plus(Duration.ofDays(1));

		var added = availabilityService.addTimeOff(new NexHomePartnerTimeOffCreateRequest(PARTNER_ID, start, end,
				"  Festival  "));
		assertThat(added.reason()).isEqualTo("Festival");

		assertThatThrownBy(() -> availabilityService.addTimeOff(new NexHomePartnerTimeOffCreateRequest(PARTNER_ID,
				end, start, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("End must be after the start.");
		assertThatThrownBy(() -> availabilityService.addTimeOff(new NexHomePartnerTimeOffCreateRequest(PARTNER_ID,
				Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1)), null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Time off cannot end in the past.");

		when(timeOffRepository.existsOverlapping(eq(PARTNER_ID), any(), any(), eq(-1L))).thenReturn(true);
		assertThatThrownBy(() -> availabilityService.addTimeOff(new NexHomePartnerTimeOffCreateRequest(PARTNER_ID,
				start, end, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("The partner already has time off in this period.");

		when(timeOffRepository.existsOverlapping(eq(PARTNER_ID), any(), any(), eq(-1L))).thenReturn(false);
		when(bookingRepository.existsPartnerBookedBetween(eq(PARTNER_ID), any(), any(), any(), eq(-1L)))
				.thenReturn(true);
		assertThatThrownBy(() -> availabilityService.addTimeOff(new NexHomePartnerTimeOffCreateRequest(PARTNER_ID,
				start, end, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("The partner has bookings in this period; unassign them first.");
	}

	@Test
	void updatingTimeOffChecksTheNewPeriodWithoutItself() {
		NexHomePartnerTimeOff timeOff = new NexHomePartnerTimeOff();
		timeOff.setId(5L);
		timeOff.setPartner(partner);
		timeOff.setStartsAt(Instant.now().plus(Duration.ofDays(2)));
		timeOff.setEndsAt(Instant.now().plus(Duration.ofDays(3)));
		timeOff.setReason("Leave");
		when(timeOffRepository.findById(5L)).thenReturn(Optional.of(timeOff));
		Instant newEnd = timeOff.getEndsAt().plus(Duration.ofDays(1));

		var updated = availabilityService.updateTimeOff(new NexHomePartnerTimeOffUpdateRequest(5L, null, newEnd, ""));

		assertThat(updated.endsAt()).isEqualTo(newEnd);
		assertThat(updated.reason()).isNull();
		verify(timeOffRepository).existsOverlapping(eq(PARTNER_ID), any(), eq(newEnd), eq(5L));
	}

	@Test
	void othersNeedTheAvailabilityFunction() {
		when(permissionService.isSelfOrCan(OWNER_ID, "PARTNER_AVAILABILITY_MANAGE", "READ")).thenReturn(false);

		assertThatThrownBy(() -> availabilityService.getTimeOff(PARTNER_ID, false))
				.isInstanceOf(AccessDeniedException.class);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Free partners
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void freePartnersOfferTheServiceWorkThenAndAreNotOffOrBooked() {
		LocalDate monday = nextMonday();
		NexHomePartnerProfile busyPartner = otherPartner(31L);
		NexHomePartnerProfile offPartner = otherPartner(32L);
		NexHomePartnerProfile pendingPartner = otherPartner(33L);
		pendingPartner.setOnboardingStatus("UNDER_REVIEW");
		when(partnerServiceRepository.findByServiceIdAndActiveTrue(SERVICE_ID)).thenReturn(List.of(
				offeringOf(partner, false, (short) 2), offeringOf(busyPartner, true, (short) 9),
				offeringOf(offPartner, true, (short) 9), offeringOf(pendingPartner, true, (short) 9)));
		when(timeOffRepository.findPartnersOffBetween(any(), any(), any())).thenReturn(List.of(32L));
		when(bookingRepository.findPartnersBookedBetween(any(), any(), any(), any())).thenReturn(List.of(31L));

		var free = availabilityService.findAvailablePartners(SERVICE_ID, at(monday, 9, 0), at(monday, 10, 0), false);

		assertThat(free).extracting("partnerProfileId").containsExactly(PARTNER_ID);
		// Nobody works on Wednesday
		assertThat(availabilityService.findAvailablePartners(SERVICE_ID, at(monday.plusDays(2), 9, 0),
				at(monday.plusDays(2), 10, 0), false)).isEmpty();
		// With the busy ones: why each is busy, the free one first
		assertThat(availabilityService.findAvailablePartners(SERVICE_ID, at(monday, 9, 0), at(monday, 10, 0), true))
				.extracting("partnerProfileId", "busyReason")
				.containsExactly(org.assertj.core.groups.Tuple.tuple(PARTNER_ID, null),
						org.assertj.core.groups.Tuple.tuple(31L, "BOOKED"),
						org.assertj.core.groups.Tuple.tuple(32L, "TIME_OFF"));
		verify(timeOffRepository, never()).existsOverlapping(anyLong(), any(), any(), anyLong());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------------------------

	private static LocalDate nextMonday() {
		return LocalDate.now(NexHomeAvailabilityServiceImpl.ZONE).plusDays(1)
				.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
	}

	private static Instant at(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute).atZone(NexHomeAvailabilityServiceImpl.ZONE).toInstant();
	}

	private static NexHomeLookupValueResponse slot(String code, String start, String end) {
		return new NexHomeLookupValueResponse(1L, 99L, "AVAILABILITY_SLOT", code, code, null, 1, true, false,
				Map.of("start", start, "end", end), null);
	}

	private NexHomePartnerProfile otherPartner(Long id) {
		NexHomeUser user = new NexHomeUser();
		user.setId(id + 100);
		user.setAccountStatus("ACTIVE");
		NexHomePartnerProfile other = new NexHomePartnerProfile();
		other.setId(id);
		other.setUser(user);
		other.setOnboardingStatus("APPROVED");
		other.setWeeklyAvailability(partner.getWeeklyAvailability());
		return other;
	}

	private NexHomePartnerService offering(Long serviceId, boolean active) {
		NexHomeService service = new NexHomeService();
		service.setId(serviceId);
		NexHomePartnerService offered = new NexHomePartnerService();
		offered.setPartner(partner);
		offered.setService(service);
		offered.setActive(active);
		return offered;
	}

	private static NexHomePartnerService offeringOf(NexHomePartnerProfile partner, boolean primary, short years) {
		NexHomePartnerService offered = new NexHomePartnerService();
		offered.setPartner(partner);
		offered.setPrimaryService(primary);
		offered.setExperienceYears(years);
		offered.setActive(true);
		return offered;
	}
}
