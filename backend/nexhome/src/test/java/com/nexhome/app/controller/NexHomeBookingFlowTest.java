package com.nexhome.app.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.mapper.NexHomeAuthRefreshTokenMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Module 6 end to end against the dev database (rolled back): the cart and checkout, a staff booking, offering it to
 * a partner who accepts, the trip and the service timer to completion, cancelling, and who may see or do what. Staff
 * calls use a super user, so the dev database's grants do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeBookingFlowTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private NexHomeSecurityProperties securityProperties;
	@Autowired
	private NexHomeRoleRepository roleRepository;
	@Autowired
	private NexHomeUserRepository userRepository;
	@Autowired
	private NexHomeUserRoleRepository userRoleRepository;
	@Autowired
	private NexHomeUserMapper userMapper;
	@Autowired
	private NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	@Autowired
	private NexHomeAuthRefreshTokenMapper refreshTokenMapper;
	@Autowired
	private EntityManager entityManager;

	@Test
	void aBookingGoesFromCheckoutToCompletion() throws Exception {
		String staff = accessToken(newUser("SUPER_USER").getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Bookings %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		long serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Fan Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 400, "discountPrice": 350, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 15, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();

		// A customer with an address, and an approved partner working Monday mornings
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());
		long customerId = json(perform(post("/api/profiles/customers/create"), customer, """
				{"userId": %d, "firstName": "Anitha", "lastName": "Rao"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("customerProfileId").asLong();
		long addressId = json(perform(post("/api/addresses/create"), customer, """
				{"userId": %d, "contactName": "Anitha", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 12, Road 3", "city": "Hyderabad", "state": "Telangana",
				 "postalCode": "500081"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("addressId").asLong();
		NexHomeUser partnerUser = newUser("PARTNER");
		String partner = accessToken(partnerUser.getId());
		long partnerId = approvedPartner(staff, partnerUser, categoryId, serviceId);
		LocalDate monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		String start = monday.atTime(9, 30).atZone(IST).toInstant().toString();

		// Cart and checkout: priced on the server, one booking per item, cash bookings are confirmed at once
		perform(post("/api/cart/items/add"), customer, "{\"serviceId\": %d}".formatted(serviceId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.estimatedTotal").value(350));
		perform(post("/api/cart/items/add"), staff, "{\"serviceId\": %d}".formatted(serviceId))
				.andExpect(status().isForbidden());
		JsonNode checkout = json(perform(post("/api/cart/checkout"), customer, """
				{"addressId": %d, "bookingType": "SCHEDULED", "scheduledStartAt": "%s", "paymentMethod": "CASH"}"""
				.formatted(addressId, start))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookings[0].bookingStatus").value("PENDING"))
				.andExpect(jsonPath("$.bookings[0].paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.bookings[0].subtotalAmount").value(400))
				.andExpect(jsonPath("$.bookings[0].discountAmount").value(50))
				.andExpect(jsonPath("$.bookings[0].totalAmount").value(350))
				.andExpect(jsonPath("$.bookings[0].address.city").value("Hyderabad")));
		long bookingId = checkout.get("bookings").get(0).get("bookingId").asLong();
		perform(get("/api/cart/me"), customer, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));
		perform(post("/api/cart/checkout"), customer, """
				{"addressId": %d, "bookingType": "SCHEDULED", "scheduledStartAt": "%s", "paymentMethod": "CASH"}"""
				.formatted(addressId, start))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The cart is empty."));

		// Who sees it
		perform(get("/api/bookings/" + bookingId), customer, null).andExpect(status().isOk());
		perform(get("/api/bookings/" + bookingId), otherCustomer, null).andExpect(status().isForbidden());
		perform(get("/api/bookings/" + bookingId), partner, null).andExpect(status().isForbidden());
		perform(get("/api/bookings/me"), customer, null)
				.andExpect(jsonPath("$.content[*].bookingId", hasItem((int) bookingId)));
		perform(get("/api/bookings?customerProfileId=" + customerId), staff, null)
				.andExpect(jsonPath("$.totalElements").value(1));
		perform(get("/api/bookings"), customer, null).andExpect(status().isForbidden());
		// The address is now in use
		perform(delete("/api/addresses/delete/" + addressId), customer, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("The address is used by bookings and cannot be deleted."));
		// Every booking starts PENDING: staff confirm the cash booking before a partner is offered it (V38)
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

		// Offered to the partner, who sees it and accepts; the partner's time is booked
		long assignmentId = json(perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignmentStatus").value("OFFERED"))).get("assignmentId").asLong();
		perform(get("/api/bookings/offers/me"), partner, null)
				.andExpect(jsonPath("$.content[0].assignmentId").value(assignmentId));
		perform(get("/api/bookings/" + bookingId), partner, null).andExpect(status().isOk());
		perform(put("/api/bookings/assignments/respond"), partner,
				"{\"assignmentId\": %d, \"accept\": true}".formatted(assignmentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignmentStatus").value("ACCEPTED"));
		// Accepting moves the booking to PARTNER_EN_ROUTE, shown as "Partner Accepted"
		perform(get("/api/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("PARTNER_EN_ROUTE"));
		// The partner is now busy then: the booking itself (no slot is held)
		String jobEnd = monday.atTime(10, 30).atZone(IST).toInstant().toString();
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s&includeBusy=true".formatted(
				serviceId, start, jobEnd)), staff, null)
				.andExpect(jsonPath("$[?(@.partnerProfileId == %d)].busyReason".formatted(partnerId)).value("BOOKED"));

		// The trip and the timer
		perform(put("/api/bookings/status/update"), customer,
				"{\"bookingId\": %d, \"bookingStatus\": \"PARTNER_EN_ROUTE\"}".formatted(bookingId))
				.andExpect(status().isForbidden());
		perform(put("/api/bookings/status/update"), partner,
				"{\"bookingId\": %d, \"bookingStatus\": \"PARTNER_EN_ROUTE\"}".formatted(bookingId))
				.andExpect(status().isBadRequest());
		perform(post("/api/bookings/location"), partner,
				"{\"bookingId\": %d, \"latitude\": 17.385044123, \"longitude\": 78.486671}".formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.latitude").value(17.3850441));
		perform(get("/api/bookings/location/" + bookingId), customer, null)
				.andExpect(jsonPath("$.longitude").value(78.486671));
		perform(post("/api/bookings/timer"), partner, "{\"bookingId\": %d, \"eventType\": \"PAUSE\"}".formatted(bookingId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The timer cannot PAUSE a booking that is PARTNER_EN_ROUTE."));
		for (String event : List.of("START", "PAUSE", "RESUME")) {
			perform(post("/api/bookings/timer"), partner,
					"{\"bookingId\": %d, \"eventType\": \"%s\"}".formatted(bookingId, event))
					.andExpect(status().isCreated());
		}
		// Working: the partner's assignment is "Started Working"
		perform(get("/api/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.assignments[0].assignmentStatus").value("STARTED"));
		perform(put("/api/bookings/cancel"), customer,
				"{\"bookingId\": %d, \"reason\": \"Changed my mind\"}".formatted(bookingId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A booking that is IN_PROGRESS cannot be cancelled."));
		perform(post("/api/bookings/timer"), partner, "{\"bookingId\": %d, \"eventType\": \"STOP\"}".formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.booking.bookingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.booking.actualEndAt").isNotEmpty())
				.andExpect(jsonPath("$.timeLogs.length()").value(4))
				.andExpect(jsonPath("$.assignments[0].assignmentStatus").value("COMPLETED"))
				// PENDING at checkout, then CONFIRMED, PARTNER_EN_ROUTE (accepted), IN_PROGRESS, PAUSED,
				// IN_PROGRESS, COMPLETED
				.andExpect(jsonPath("$.history.length()").value(7))
				.andExpect(jsonPath("$.history[0].newStatus").value("PENDING"));
		// Done: the partner is free again then
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start,
				jobEnd)), staff, null)
				.andExpect(jsonPath("$[*].partnerProfileId", org.hamcrest.Matchers.hasItem((int) partnerId)));
		perform(get("/api/bookings/stats"), staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.byStatus.COMPLETED").isNumber())
				.andExpect(jsonPath("$.addedThisMonth").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.months.length()").value(10));
		// The partner's figures: one job, completed; the cash is not collected yet, so nothing earned
		perform(get("/api/bookings/partner-stats/" + partnerId), partner, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalJobs").value(1))
				.andExpect(jsonPath("$.assigned").value(0))
				.andExpect(jsonPath("$.accepted").value(0))
				.andExpect(jsonPath("$.inProgress").value(0))
				.andExpect(jsonPath("$.rejected").value(0))
				.andExpect(jsonPath("$.completed").value(1))
				.andExpect(jsonPath("$.cancelled").value(0))
				.andExpect(jsonPath("$.totalEarnings").value(0));
		perform(get("/api/bookings/partner-stats/" + partnerId), customer, null)
				.andExpect(status().isForbidden());
		// The customer's figures: one booking, completed; cash not collected yet, so nothing spent
		perform(get("/api/bookings/customer-stats/" + customerId), customer, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalBookings").value(1))
				.andExpect(jsonPath("$.pending").value(0))
				.andExpect(jsonPath("$.confirmed").value(0))
				.andExpect(jsonPath("$.partnerAssigned").value(0))
				.andExpect(jsonPath("$.accepted").value(0))
				.andExpect(jsonPath("$.inProgress").value(0))
				.andExpect(jsonPath("$.partnerRejected").value(0))
				.andExpect(jsonPath("$.completed").value(1))
				.andExpect(jsonPath("$.cancelled").value(0))
				.andExpect(jsonPath("$.totalSpend").value(0));
		perform(get("/api/bookings/customer-stats/" + customerId), partner, null)
				.andExpect(status().isForbidden());
	}

	@Test
	void staffBookForCustomersAndCancellingFreesThePartner() throws Exception {
		String staff = accessToken(newUser("SUPER_USER").getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Bookings %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		long serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Sink Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 300, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 15, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		long customerId = json(perform(post("/api/profiles/customers/create"), customer, """
				{"userId": %d, "firstName": "Ravi", "lastName": "Kumar"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("customerProfileId").asLong();
		long addressId = json(perform(post("/api/addresses/create"), customer, """
				{"userId": %d, "contactName": "Ravi", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 9", "city": "Nuzvid", "state": "Andhra Pradesh",
				 "postalCode": "521201"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("addressId").asLong();
		NexHomeUser partnerUser = newUser("PARTNER");
		long partnerId = approvedPartner(staff, partnerUser, categoryId, serviceId);
		LocalDate monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		String start = monday.atTime(10, 0).atZone(IST).toInstant().toString();

		// An online booking waits for its payment; staff confirm it by hand until payments exist (Module 7)
		long bookingId = json(perform(post("/api/bookings/create"), staff, """
				{"customerProfileId": %d, "serviceId": %d, "addressId": %d, "bookingType": "SCHEDULED",
				 "scheduledStartAt": "%s", "paymentMethod": "ONLINE"}""".formatted(customerId, serviceId, addressId,
				start))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookingStatus").value("PENDING"))
				.andExpect(jsonPath("$.paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.publicId").value(org.hamcrest.Matchers.startsWith("NHBOOK")))).get("bookingId")
				.asLong();
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d}".formatted(bookingId, partnerId))
				.andExpect(status().isBadRequest());
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId))
				.andExpect(status().isOk());
		// Assigned directly (ASSIGNED until the partner accepts); the partner's time is then booked for anyone else
		long directId = json(perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"))).get("assignmentId").asLong();
		perform(put("/api/bookings/assignments/respond"), accessToken(partnerUser.getId()),
				"{\"assignmentId\": %d, \"accept\": true}".formatted(directId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignmentStatus").value("ACCEPTED"));
		perform(get("/api/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("PARTNER_EN_ROUTE"))
				.andExpect(jsonPath("$.assignments[0].assignedByName").isNotEmpty());
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start,
				monday.atTime(11, 0).atZone(IST).toInstant())), staff, null)
				.andExpect(jsonPath("$.length()").value(0));

		// Cancelling needs a reason, ends the assignment and frees the partner
		perform(put("/api/bookings/cancel"), customer, "{\"bookingId\": %d, \"reason\": \" \"}".formatted(bookingId))
				.andExpect(status().isBadRequest());
		perform(put("/api/bookings/cancel"), customer,
				"{\"bookingId\": %d, \"reason\": \"Plumber came early\"}".formatted(bookingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingStatus").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("Plumber came early"));
		perform(get("/api/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.assignments[0].assignmentStatus").value("CANCELLED"));
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start,
				monday.atTime(11, 0).atZone(IST).toInstant())), staff, null)
				.andExpect(jsonPath("$.length()").value(1));

		// An INSTANT booking has no times until staff allot a slot when assigning; it then holds the partner's time.
		// Its item keeps the option as booked, even once the option is deleted.
		long variantId = json(perform(post("/api/catalog/variants/add"), staff, """
				{"serviceId": %d, "variantCode": "KITCHEN_SINK", "name": "Kitchen Sink", "priceOverride": 450}"""
				.formatted(serviceId))
				.andExpect(status().isCreated())).get("serviceVariantId").asLong();
		long instantId = json(perform(post("/api/bookings/create"), staff, """
				{"customerProfileId": %d, "serviceId": %d, "serviceVariantId": %d, "addressId": %d,
				 "bookingType": "INSTANT", "paymentMethod": "CASH"}""".formatted(customerId, serviceId, variantId,
				addressId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.scheduledStartAt").doesNotExist())
				.andExpect(jsonPath("$.bookingStatus").value("PENDING"))
				.andExpect(jsonPath("$.scheduledEndAt").doesNotExist())).get("bookingId").asLong();
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(instantId))
				.andExpect(status().isOk());
		// One transaction here (rolled back): forget the loaded booking, as a later request would
		entityManager.flush();
		entityManager.clear();
		perform(delete("/api/catalog/variants/delete/" + variantId), staff, null).andExpect(status().isNoContent());
		entityManager.flush();
		entityManager.clear();
		perform(get("/api/bookings/" + instantId), staff, null)
				.andExpect(jsonPath("$.items[0].variant.serviceVariantId").value(variantId))
				.andExpect(jsonPath("$.items[0].variant.name").value("Kitchen Sink"))
				.andExpect(jsonPath("$.categoryId").value(categoryId))
				.andExpect(jsonPath("$.items[0].variant.price").value(450));
		String slotEnd = monday.atTime(11, 30).atZone(IST).toInstant().toString();
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"startsAt\": \"%s\"}".formatted(instantId, partnerId,
						start))
				.andExpect(status().isBadRequest());
		long instantAssignmentId = json(perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true, \"startsAt\": \"%s\", \"endsAt\": \"%s\"}"
						.formatted(instantId, partnerId, start, slotEnd))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"))).get("assignmentId").asLong();
		perform(get("/api/bookings/" + instantId), staff, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("ASSIGNED"))
				.andExpect(jsonPath("$.booking.scheduledStartAt").exists())
				.andExpect(jsonPath("$.booking.scheduledEndAt").exists());
		// The partner's own "Partner Accepted" status update accepts the assignment too
		perform(put("/api/bookings/status/update"), accessToken(partnerUser.getId()),
				"{\"bookingId\": %d, \"bookingStatus\": \"PARTNER_EN_ROUTE\"}".formatted(instantId))
				.andExpect(status().isOk());
		perform(get("/api/bookings/" + instantId), staff, null)
				.andExpect(jsonPath("$.assignments[0].assignmentStatus").value("ACCEPTED"));
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start,
				slotEnd)), staff, null)
				.andExpect(jsonPath("$.length()").value(0));

		// Unassigning frees the partner and takes the allotted slot off the INSTANT booking again
		perform(put("/api/bookings/assignments/cancel"), staff,
				"{\"assignmentId\": %d}".formatted(instantAssignmentId))
				.andExpect(status().isOk());
		perform(get("/api/bookings/" + instantId), staff, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.booking.partnerProfileId").doesNotExist())
				.andExpect(jsonPath("$.booking.scheduledStartAt").doesNotExist())
				.andExpect(jsonPath("$.booking.scheduledEndAt").doesNotExist());
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start,
				slotEnd)), staff, null)
				.andExpect(jsonPath("$.length()").value(1));
	}

	/** An approved partner who offers the service and works Monday mornings and evenings. */
	private long approvedPartner(String staff, NexHomeUser partnerUser, long categoryId, long serviceId)
			throws Exception {
		long partnerId = json(perform(post("/api/partners/profiles/create"), staff, """
				{"userId": %d, "firstName": "Kiran", "lastName": "Rao"}""".formatted(partnerUser.getId()))
				.andExpect(status().isCreated())).get("partnerProfileId").asLong();
		perform(put("/api/partners/profiles/update"), staff, """
				{"partnerProfileId": %d, "mainServiceCategoryId": %d, "isAdult": true, "preferredContactLanguage": "te",
				 "aadhaarNumber": "234567890123", "addressLine1": "H.No 1-2-3", "locality": "Madhapur",
				 "city": "Hyderabad", "state": "Telangana", "postalCode": "500081", "skillCategory": "SKILLED",
				 "yearsExperience": 6, "preferredArrangement": "FLEXIBLE_JOBS", "paymentBasis": "PER_JOB",
				 "expectedEarningsInr": 800, "maxTravelKm": 10, "serviceAreas": ["Madhapur"],
				 "weeklyAvailability": {"MONDAY": ["MORNING", "EVENING"]}, "weekendAvailability": "NEITHER",
				 "emergencyContactName": "Lakshmi", "emergencyContactRelationship": "SPOUSE",
				 "emergencyContactNumber": "9000000001"}""".formatted(partnerId, categoryId))
				.andExpect(status().isOk());
		perform(post("/api/partners/services/add"), staff,
				"{\"partnerProfileId\": %d, \"serviceId\": %d}".formatted(partnerId, serviceId))
				.andExpect(status().isCreated());
		perform(put("/api/partners/profiles/submit"), staff, "{\"partnerProfileId\": %d}".formatted(partnerId))
				.andExpect(status().isOk());
		entityManager.createNativeQuery("update nexhome_partner_profiles set onboarding_status = 'APPROVED', "
				+ "approval_status = 'VERIFIED', approved_by = 1, approved_at = now() where id = :id")
				.setParameter("id", partnerId).executeUpdate();
		entityManager.clear();
		return partnerId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String accessToken, String body)
			throws Exception {
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode json(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	/** An ACTIVE account with one role, created inside the test's transaction. */
	private NexHomeUser newUser(String roleCode) {
		NexHomeUser user = userRepository.save(userMapper.toUserEntity("+91", randomMobile(), null, null, 1L));
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user,
				roleRepository.findByCode(roleCode).orElseThrow(), 1L));
		return user;
	}

	/** A login session and its access token, as the auth service would issue them. */
	private String accessToken(Long userId) {
		Instant now = Instant.now();
		UUID sessionId = UUID.randomUUID();
		refreshTokenRepository.saveAndFlush(refreshTokenMapper.toRefreshTokenEntity(
				userRepository.findById(userId).orElseThrow(), sessionId, UUID.randomUUID().toString().replace("-", "")
						+ UUID.randomUUID().toString().replace("-", ""),
				now.plusSeconds(3600), "JUnit"));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(securityProperties.jwtIssuer())
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", sessionId.toString())
				.claim("roles", List.of())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private static String randomMobile() {
		return "8" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
	}
}
