package com.nexhome.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.nexhome.app.dto.NexHomeSettingResponse;
import com.nexhome.app.dto.NexHomeSettingsUpdateRequest;

/**
 * General settings (V49): business and contact details, booking rules and staff alerts. Each has a default in the
 * code; a stored value replaces it. Other services read them through the typed getters.
 */
public interface NexHomeSettingsService {

	/** The setting keys code reads. */
	final class Keys {

		// Business and contact (public: the website shows them)
		public static final String SUPPORT_PHONE = "SUPPORT_PHONE";
		public static final String WHATSAPP_NUMBER = "WHATSAPP_NUMBER";
		public static final String SUPPORT_EMAIL = "SUPPORT_EMAIL";
		public static final String SERVICE_AREA = "SERVICE_AREA";
		public static final String WORKING_HOURS = "WORKING_HOURS";

		// Booking rules
		public static final String TRANSACTION_FEE_PERCENTAGE = "TRANSACTION_FEE_PERCENTAGE";
		public static final String OFFER_EXPIRY_MINUTES = "OFFER_EXPIRY_MINUTES";
		public static final String UNASSIGNED_ALERT_HOURS = "UNASSIGNED_ALERT_HOURS";
		public static final String MAX_DAYS_AHEAD = "MAX_DAYS_AHEAD";
		public static final String MAX_CART_ITEMS = "MAX_CART_ITEMS";

		// Staff alerts (OFF, NORMAL or HIGH), one per event NexHomeNotifier.notifyStaff sends
		public static final String ALERT_PARTNER_SIGNUP = "ALERT_PARTNER_SIGNUP";
		public static final String ALERT_PARTNER_SUBMITTED = "ALERT_PARTNER_SUBMITTED";
		public static final String ALERT_PARTNER_TO_VERIFY = "ALERT_PARTNER_TO_VERIFY";
		public static final String ALERT_NEW_BOOKING = "ALERT_NEW_BOOKING";
		public static final String ALERT_BOOKING_CANCELLED = "ALERT_BOOKING_CANCELLED";
		public static final String ALERT_PARTNER_DECLINED = "ALERT_PARTNER_DECLINED";
		public static final String ALERT_OFFER_EXPIRED = "ALERT_OFFER_EXPIRED";
		public static final String ALERT_BOOKING_UNASSIGNED = "ALERT_BOOKING_UNASSIGNED";
		public static final String ALERT_PAYMENT_FAILED = "ALERT_PAYMENT_FAILED";
		public static final String ALERT_CASH_DISPUTED = "ALERT_CASH_DISPUTED";
		public static final String ALERT_PAYOUT_FAILED = "ALERT_PAYOUT_FAILED";
		public static final String ALERT_TICKET_OPENED = "ALERT_TICKET_OPENED";
		public static final String ALERT_TICKET_RAISED = "ALERT_TICKET_RAISED";
		public static final String ALERT_TICKET_REPLY = "ALERT_TICKET_REPLY";
		public static final String ALERT_LOW_RATING = "ALERT_LOW_RATING";

		private Keys() {
		}
	}

	/** Every setting with its definition and current value, by group and order. */
	List<NexHomeSettingResponse> getSettings();

	/** The public settings (business and contact details) as key to value, for the website; no token needed. */
	Map<String, String> getPublicSettings();

	/** Changes the given settings at once; returns every setting. */
	List<NexHomeSettingResponse> updateSettings(NexHomeSettingsUpdateRequest request);

	int intValue(String settingKey);

	BigDecimal decimalValue(String settingKey);

	/** A staff alert's setting: OFF, NORMAL or HIGH. */
	String alertLevel(String alertKey);
}
