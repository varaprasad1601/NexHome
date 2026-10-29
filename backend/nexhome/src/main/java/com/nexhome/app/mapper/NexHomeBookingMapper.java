package com.nexhome.app.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeBookingAssignmentResponse;
import com.nexhome.app.dto.NexHomeBookingItemResponse;
import com.nexhome.app.dto.NexHomeBookingResponse;
import com.nexhome.app.dto.NexHomeBookingTimeLogResponse;
import com.nexhome.app.dto.NexHomeCartItemResponse;
import com.nexhome.app.dto.NexHomePartnerLocationResponse;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeBookingAssignment;
import com.nexhome.app.model.NexHomeBookingItem;
import com.nexhome.app.model.NexHomeBookingTimeLog;
import com.nexhome.app.model.NexHomeCartItem;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.CommissionType;
import com.nexhome.app.model.NexHomePartnerLocationUpdate;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceVariant;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Carts, bookings and their items, assignments, timer events and locations (Module 6). */
@Component
@RequiredArgsConstructor
public class NexHomeBookingMapper {

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	public NexHomeBookingResponse toBookingDto(NexHomeBooking booking) {
		NexHomeCustomerProfile customer = booking.getCustomer();
		NexHomePartnerProfile partner = booking.getPartner();
		return new NexHomeBookingResponse(
				booking.getId(),
				booking.getPublicId(),
				booking.getCheckoutId(),
				customer.getId(),
				customer.getPublicId(),
				nameOf(customer.getFirstName(), customer.getLastName()),
				customer.getUser().getId(),
				booking.getService().getId(),
				booking.getServiceNameSnapshot(),
				booking.getVariant() == null ? null : booking.getVariant().getId(),
				partner == null ? null : partner.getId(),
				partner == null ? null : partner.getPublicId(),
				partner == null ? null : nameOf(partner.getFirstName(), partner.getLastName()),
				booking.getAddress().getId(),
				booking.getBookingType(),
				booking.getBookingStatus(),
				booking.getPaymentStatus(),
				booking.getPaymentMethod(),
				booking.getScheduledStartAt(),
				booking.getScheduledEndAt(),
				booking.getActualStartAt(),
				booking.getActualEndAt(),
				readObject(booking.getAddressSnapshot()),
				readObject(booking.getPricingSnapshot()),
				booking.getSubtotalAmount(),
				booking.getDiscountAmount(),
				booking.getTaxAmount(),
				booking.getTransactionFeeAmount(),
				booking.getTotalAmount(),
				booking.getCurrency(),
				booking.getCancellationReason(),
				booking.getCreationDate());
	}

	public NexHomeBookingItemResponse toBookingItemDto(NexHomeBookingItem item) {
		return new NexHomeBookingItemResponse(item.getId(), item.getService().getId(), item.getItemNameSnapshot(),
				readObject(item.getVariantSnapshot()), item.getUnitPrice(), item.getLineTotal());
	}

	/** The variant as booked, for {@code variant_snapshot}; null without one. */
	public String toVariantSnapshot(NexHomeServiceVariant variant) {
		if (variant == null) {
			return null;
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("serviceVariantId", variant.getId());
		snapshot.put("publicId", variant.getPublicId());
		snapshot.put("variantCode", variant.getVariantCode());
		snapshot.put("name", variant.getName());
		snapshot.put("price", variant.getPriceOverride());
		snapshot.put("discountPrice", variant.getDiscountPrice());
		snapshot.put("durationMinutes", variant.getDurationMinutes());
		return toJson(snapshot);
	}

	public NexHomeBookingAssignmentResponse toAssignmentDto(NexHomeBookingAssignment assignment) {
		NexHomePartnerProfile partner = assignment.getPartner();
		return new NexHomeBookingAssignmentResponse(
				assignment.getId(),
				assignment.getBooking().getId(),
				assignment.getBooking().getPublicId(),
				partner.getId(),
				partner.getPublicId(),
				nameOf(partner.getFirstName(), partner.getLastName()),
				assignment.getAssignedBy(),
				assignment.getAssignmentStatus(),
				assignment.getOfferedAt(),
				assignment.getRespondedAt(),
				assignment.getExpiresAt(),
				assignment.getRejectionReason(), null, null);
	}

	public NexHomeBookingTimeLogResponse toTimeLogDto(NexHomeBookingTimeLog log) {
		return new NexHomeBookingTimeLogResponse(log.getId(), log.getEventType(), log.getEventAt(),
				log.getRecordedBy(), log.getNotes());
	}

	public NexHomePartnerLocationResponse toLocationDto(NexHomePartnerLocationUpdate location) {
		return new NexHomePartnerLocationResponse(location.getPartner().getId(),
				location.getBooking() == null ? null : location.getBooking().getId(), location.getLatitude(),
				location.getLongitude(), location.getRecordedAt());
	}

	public NexHomeCartItemResponse toCartItemDto(NexHomeCartItem item) {
		return new NexHomeCartItemResponse(item.getId(), item.getService().getId(), item.getService().getName(),
				item.getVariant() == null ? null : item.getVariant().getId(),
				item.getVariant() == null ? null : item.getVariant().getName(), item.getEstimatedUnitPrice(),
				item.getCreationDate());
	}

	/** The address as booked, for {@code address_snapshot}. */
	public String toAddressSnapshot(NexHomeAddress address) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("addressId", address.getId());
		snapshot.put("publicId", address.getPublicId());
		snapshot.put("contactName", address.getContactName());
		snapshot.put("mobileNumber", address.getMobileNumber());
		snapshot.put("addressType", address.getAddressType());
		snapshot.put("label", address.getLabel());
		snapshot.put("addressLine1", address.getAddressLine1());
		snapshot.put("addressLine2", address.getAddressLine2());
		snapshot.put("landmark", address.getLandmark());
		snapshot.put("city", address.getCity());
		snapshot.put("state", address.getState());
		snapshot.put("postalCode", address.getPostalCode());
		snapshot.put("countryCode", address.getCountryCode());
		snapshot.put("latitude", address.getLatitude());
		snapshot.put("longitude", address.getLongitude());
		return toJson(snapshot);
	}

	/**
	 * The platform's commission of a service on an amount: its own platform commission (no commission rules for now),
	 * a PERCENTAGE of it to the paisa (HALF_UP) or the FIXED amount, never more than the amount; 0 without one.
	 */
	public static BigDecimal commissionOf(NexHomeService service, BigDecimal amount) {
		if (service.getPlatformCommissionType() == null || service.getPlatformCommissionValue() == null) {
			return BigDecimal.ZERO;
		}
		BigDecimal value = service.getPlatformCommissionValue();
		BigDecimal commission = CommissionType.PERCENTAGE.equals(service.getPlatformCommissionType())
				? amount.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
				: value.setScale(2, RoundingMode.HALF_UP);
		return commission.min(amount);
	}

	public String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	public Map<String, Object> readObject(String json) {
		return json == null ? null : jsonMapper.readValue(json, JSON_OBJECT);
	}

	/** "First Last", or null while the profile has no name. */
	public static String nameOf(String firstName, String lastName) {
		return NexHomeText.trimToNull(String.join(" ", firstName == null ? "" : firstName,
				lastName == null ? "" : lastName));
	}
}
