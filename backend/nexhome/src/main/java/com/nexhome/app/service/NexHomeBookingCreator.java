package com.nexhome.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeBookingItem;
import com.nexhome.app.model.NexHomeBookingStatusHistory;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingPaymentStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingType;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentMethod;
import com.nexhome.app.model.NexHomeLookupCodes.PricingType;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceVariant;
import com.nexhome.app.repository.NexHomeAddressRepository;
import com.nexhome.app.repository.NexHomeBookingItemRepository;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeBookingStatusHistoryRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeServiceVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes one booking (Module 6), for checkout and for staff booking on a customer's behalf: checks the customer,
 * service, variant, address, type and time, prices it on the server with the catalogue's quote, and saves the booking
 * with its line item, snapshots and first status history row. Runs inside the caller's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NexHomeBookingCreator {

	/** SCHEDULED bookings may be made this far ahead. */
	static final Duration LONGEST_AHEAD = Duration.ofDays(60);
	/** The first history row's reason for a booking made without a free partner. */
	static final String SLOT_REQUESTED = "Slot requested: no partner was free when booked";
	/** A booking without an expected duration is held for this long. */
	static final int DEFAULT_DURATION_MINUTES = 60;

	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeBookingItemRepository bookingItemRepository;
	private final NexHomeBookingStatusHistoryRepository historyRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomeServiceVariantRepository variantRepository;
	private final NexHomeAddressRepository addressRepository;
	private final NexHomeCatalogService catalogService;
	private final NexHomeLookupService lookupService;
	private final NexHomeBookingMapper bookingMapper;

	/** The request's parts, already looked up by id where the caller had to. */
	record Order(NexHomeCustomerProfile customer, Long serviceId, Long variantId, Long addressId, String bookingType,
			Instant scheduledStartAt, String paymentMethod, Integer minutes, UUID checkoutId, boolean slotRequested) {
	}

	NexHomeBooking create(Order order, Long actingUserId) {
		NexHomeCustomerProfile customer = order.customer();
		if (!AccountStatus.ACTIVE.equals(customer.getUser().getAccountStatus())) {
			throw new InvalidRequestException("The customer's account is not active.");
		}
		NexHomeService service = serviceRepository.findWithCategoryById(order.serviceId())
				.filter(found -> found.isActive() && found.getCategory().isActive())
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist or is not offered."));
		NexHomeServiceVariant variant = null;
		if (order.variantId() != null) {
			variant = variantRepository.findById(order.variantId())
					.filter(found -> found.getService().getId().equals(service.getId()))
					.orElseThrow(() -> new InvalidRequestException("The variant is not an option of this service."));
			if (!variant.isActive()) {
				// A switched-off hourly package is booked as one hour at the service's price; other options must be on
				if (!PricingType.HOURLY.equals(service.getPricingType())) {
					throw new InvalidRequestException("The variant is not an active option of this service.");
				}
				variant = null;
			}
		}
		NexHomeAddress address = addressRepository.findById(order.addressId())
				.orElseThrow(() -> new ResourceNotFoundException("Address does not exist."));
		if (!address.getUser().getId().equals(customer.getUser().getId())) {
			throw new InvalidRequestException("The address is not the customer's.");
		}
		String bookingType = lookupService.validateCode(Types.BOOKING_TYPE, order.bookingType(), "Booking type");
		String paymentMethod = lookupService.validateCode(Types.PAYMENT_METHOD, order.paymentMethod(),
				"Payment method");
		Instant start = startOf(bookingType, order.scheduledStartAt(), service);

		NexHomeServiceQuoteResponse quote = catalogService.quote(service.getId(),
				variant == null ? null : variant.getId(), order.minutes());
		int duration = quote.durationMinutes() != null ? quote.durationMinutes() : DEFAULT_DURATION_MINUTES;
		// The regular amount for the job and the discount off it (hourly jobs too: the regular hourly or package price)
		BigDecimal discount = quote.regularSubtotal().subtract(quote.subtotal());
		String itemName = variant == null ? service.getName() : service.getName() + " - " + variant.getName();

		NexHomeBooking booking = new NexHomeBooking();
		// Staff bookings are a checkout of their own, so a payment order always covers one checkout (Module 7)
		booking.setCheckoutId(order.checkoutId() != null ? order.checkoutId() : UUID.randomUUID());
		booking.setCustomer(customer);
		booking.setService(service);
		booking.setVariant(variant);
		booking.setAddress(address);
		booking.setBookingType(bookingType);
		// Every booking starts PENDING: staff confirm a cash one, an online one is confirmed by its payment (V38)
		boolean cash = PaymentMethod.CASH.equals(paymentMethod);
		booking.setBookingStatus(BookingStatus.PENDING);
		// Cash and online share the payment statuses: PENDING until paid (V40)
		booking.setPaymentStatus(BookingPaymentStatus.PENDING);
		booking.setPaymentMethod(paymentMethod);
		// An INSTANT booking has no times until the work starts (V33)
		booking.setScheduledStartAt(start);
		booking.setScheduledEndAt(start == null ? null : start.plus(Duration.ofMinutes(duration)));
		booking.setServiceNameSnapshot(truncate(itemName, 150));
		booking.setAddressSnapshot(bookingMapper.toAddressSnapshot(address));
		// Online payments carry the gateway's fee (V35), at the rate of the day, kept with the booking
		BigDecimal feePercentage = cash ? BigDecimal.ZERO : onlineFeePercentage();
		BigDecimal fee = feeOf(quote.total(), feePercentage);
		Map<String, Object> pricing = pricingOf(quote, variant);
		pricing.put("transactionFeePercentage", feePercentage);
		booking.setPricingSnapshot(bookingMapper.toJson(pricing));
		booking.setSubtotalAmount(quote.regularSubtotal());
		booking.setDiscountAmount(discount);
		booking.setTaxAmount(BigDecimal.ZERO);
		booking.setTransactionFeeAmount(fee);
		booking.setTotalAmount(quote.total().add(fee));
		booking.setCreatedBy(actingUserId);
		booking.setUpdatedBy(actingUserId);
		booking = bookingRepository.save(booking);

		NexHomeBookingItem item = new NexHomeBookingItem();
		item.setBooking(booking);
		item.setService(service);
		item.setVariantSnapshot(bookingMapper.toVariantSnapshot(variant));
		item.setItemNameSnapshot(truncate(itemName, 150));
		item.setUnitPrice(quote.unitPrice());
		item.setLineTotal(quote.total());
		item.setCreatedBy(actingUserId);
		item.setUpdatedBy(actingUserId);
		bookingItemRepository.save(item);

		recordStatus(booking, null, booking.getBookingStatus(),
				order.slotRequested() ? SLOT_REQUESTED : null, actingUserId);
		return booking;
	}

	/** INSTANT: no start (as soon as possible), if the service takes instant bookings; SCHEDULED: a future time. */
	private static Instant startOf(String bookingType, Instant requested, NexHomeService service) {
		Instant now = Instant.now();
		if (BookingType.INSTANT.equals(bookingType)) {
			if (!service.isInstantBookingAllowed()) {
				throw new InvalidRequestException("This service cannot be booked for now; schedule it instead.");
			}
			return null;
		}
		if (!service.isScheduledBookingAllowed()) {
			throw new InvalidRequestException("This service can only be booked for now (INSTANT).");
		}
		if (requested == null) {
			throw new InvalidRequestException("Scheduled start is required for a SCHEDULED booking.");
		}
		if (!requested.isAfter(now)) {
			throw new InvalidRequestException("Scheduled start must be in the future.");
		}
		if (requested.isAfter(now.plus(LONGEST_AHEAD))) {
			throw new InvalidRequestException("Bookings can be made at most 60 days ahead.");
		}
		return requested;
	}

	/** The quote as the booking's pricing snapshot. */
	/** The TRANSACTION_FEE lookup's ONLINE rate (metadata.percentage), 0 when missing, inactive or unreadable. */
	BigDecimal onlineFeePercentage() {
		return lookupService.getActiveLookupValues(Types.TRANSACTION_FEE).stream()
				.filter(value -> "ONLINE".equals(value.valueCode()))
				.map(value -> value.metadata() == null ? null : value.metadata().get("percentage"))
				.filter(Objects::nonNull)
				.findFirst()
				.map(rate -> {
					try {
						BigDecimal percentage = new BigDecimal(rate.toString());
						return percentage.signum() > 0 && percentage.compareTo(BigDecimal.valueOf(100)) <= 0
								? percentage
								: BigDecimal.ZERO;
					} catch (NumberFormatException e) {
						log.warn("TRANSACTION_FEE ONLINE has an unreadable percentage: {}", rate);
						return BigDecimal.ZERO;
					}
				})
				.orElse(BigDecimal.ZERO);
	}

	/** The fee on an amount at a percentage, to the paisa (HALF_UP). */
	static BigDecimal feeOf(BigDecimal amount, BigDecimal percentage) {
		return percentage == null || percentage.signum() <= 0 ? BigDecimal.ZERO
				: amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
	}

	static Map<String, Object> pricingOf(NexHomeServiceQuoteResponse quote, NexHomeServiceVariant variant) {
		Map<String, Object> pricing = new LinkedHashMap<>();
		pricing.put("pricingType", quote.pricingType());
		pricing.put("serviceVariantId", quote.serviceVariantId());
		pricing.put("variantName", variant == null ? null : variant.getName());
		pricing.put("regularPrice", quote.regularPrice());
		pricing.put("unitPrice", quote.unitPrice());
		pricing.put("billableMinutes", quote.billableMinutes());
		pricing.put("subtotal", quote.subtotal());
		pricing.put("regularSubtotal", quote.regularSubtotal());
		pricing.put("minimumCharge", quote.minimumCharge());
		pricing.put("total", quote.total());
		pricing.put("durationMinutes", quote.durationMinutes());
		pricing.put("pricedAt", Instant.now().toString());
		return pricing;
	}

	/** One row of the booking's status history, in the caller's transaction. */
	void recordStatus(NexHomeBooking booking, String oldStatus, String newStatus, String reason, Long actingUserId) {
		NexHomeBookingStatusHistory history = new NexHomeBookingStatusHistory();
		history.setBooking(booking);
		history.setOldStatus(oldStatus);
		history.setNewStatus(newStatus);
		history.setChangedBy(actingUserId);
		history.setReason(reason);
		history.setCreatedBy(actingUserId);
		history.setUpdatedBy(actingUserId);
		historyRepository.save(history);
	}

	private static String truncate(String text, int length) {
		return text.length() <= length ? text : text.substring(0, length);
	}
}
