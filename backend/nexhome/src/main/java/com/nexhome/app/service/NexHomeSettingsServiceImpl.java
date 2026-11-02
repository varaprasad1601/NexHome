package com.nexhome.app.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeSettingResponse;
import com.nexhome.app.dto.NexHomeSettingsUpdateRequest;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.model.NexHomeAppSetting;
import com.nexhome.app.repository.NexHomeAppSettingRepository;
import com.nexhome.app.service.NexHomeSettingsService.Keys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NexHomeSettingsServiceImpl implements NexHomeSettingsService {

	static final String BUSINESS = "BUSINESS";
	static final String BOOKING = "BOOKING";
	static final String STAFF_ALERTS = "STAFF_ALERTS";

	static final String TEXT = "TEXT";
	static final String PHONE = "PHONE";
	static final String DIGITS = "DIGITS";
	static final String EMAIL = "EMAIL";
	static final String NUMBER = "NUMBER";
	static final String DECIMAL = "DECIMAL";
	static final String ALERT = "ALERT";

	static final String OFF = "OFF";
	static final String NORMAL = "NORMAL";
	static final String HIGH = "HIGH";
	static final List<String> ALERT_LEVELS = List.of(OFF, NORMAL, HIGH);

	private static final Pattern PHONE_TEXT = Pattern.compile("^\\+?[0-9][0-9 -]{5,19}$");
	private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]{10,15}$");
	private static final Pattern EMAIL_ADDRESS = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	/** A setting: key, group, type, label, help, default, limits (min / max for numbers, maxLength for text). */
	record Definition(String key, String group, String type, String label, String description, String defaultValue,
			Double min, Double max, Integer maxLength, boolean isPublic) {

		static Definition text(String key, String type, String label, String description, String defaultValue,
				int maxLength) {
			return new Definition(key, BUSINESS, type, label, description, defaultValue, null, null, maxLength, true);
		}

		static Definition number(String key, String type, String label, String description, String defaultValue,
				double min, double max) {
			return new Definition(key, BOOKING, type, label, description, defaultValue, min, max, null, false);
		}

		static Definition alert(String key, String label, String description, String defaultValue) {
			return new Definition(key, STAFF_ALERTS, ALERT, label, description, defaultValue, null, null, null, false);
		}
	}

	/** Every setting, in the order the console shows them. Defaults are what the app used before V49. */
	static final List<Definition> DEFINITIONS = List.of(
			Definition.text(Keys.SUPPORT_PHONE, PHONE, "Support Phone",
					"The phone number customers call, as the website shows it (e.g. 9257 14 9257).", "9257 14 9257", 20),
			Definition.text(Keys.WHATSAPP_NUMBER, DIGITS, "WhatsApp Number",
					"Enquiries and registrations open WhatsApp chats with this number: digits only, with the country "
							+ "code (e.g. 919257149257).", "919257149257", 15),
			Definition.text(Keys.SUPPORT_EMAIL, EMAIL, "Support Email", "The email address the website shows.",
					"nexhome.info@gmail.com", 120),
			Definition.text(Keys.SERVICE_AREA, TEXT, "Service Area", "Where NexHome works, as the website shows it.",
					"Nuzvid and Nearby Areas, Andhra Pradesh", 150),
			Definition.text(Keys.WORKING_HOURS, TEXT, "Working Hours", "The business hours the website shows.",
					"Mon – Sun, 9:00 AM to 5:00 PM", 100),

			Definition.number(Keys.TRANSACTION_FEE_PERCENTAGE, DECIMAL, "Online Payment Fee (%)",
					"Added to online bookings' total for the payment gateway; not refunded, not paid to the partner. "
							+ "0 turns it off. Applies to new bookings.", "2", 0, 100),
			Definition.number(Keys.OFFER_EXPIRY_MINUTES, NUMBER, "Offer Expiry (minutes)",
					"How long a partner has to accept a booking offer when staff do not set a time.", "30", 1, 60),
			Definition.number(Keys.UNASSIGNED_ALERT_HOURS, NUMBER, "No-Partner Warning (hours before)",
					"Staff are warned when a confirmed booking without a partner starts within this many hours.", "2",
					1, 48),
			Definition.number(Keys.MAX_DAYS_AHEAD, NUMBER, "Book Ahead Limit (days)",
					"How far ahead a scheduled booking can be made.", "60", 1, 365),
			Definition.number(Keys.MAX_CART_ITEMS, NUMBER, "Cart Limit (services)",
					"The most services a customer's cart can hold.", "20", 1, 50),

			Definition.alert(Keys.ALERT_PARTNER_SIGNUP, "Partner signed up", "A new partner account was created.",
					NORMAL),
			Definition.alert(Keys.ALERT_PARTNER_SUBMITTED, "Partner submitted for review",
					"A partner profile was submitted or resubmitted after changes were requested.", NORMAL),
			Definition.alert(Keys.ALERT_PARTNER_TO_VERIFY, "Document or bank account to verify",
					"A document or bank account was added or changed on a profile under review or approved.", NORMAL),
			Definition.alert(Keys.ALERT_NEW_BOOKING, "New booking", "A customer checked out.", NORMAL),
			Definition.alert(Keys.ALERT_BOOKING_CANCELLED, "Booking cancelled by the customer",
					"A customer cancelled a booking.", NORMAL),
			Definition.alert(Keys.ALERT_PARTNER_DECLINED, "Partner declined a job",
					"A partner declined an offer or assignment.", NORMAL),
			Definition.alert(Keys.ALERT_OFFER_EXPIRED, "Offer expired", "A partner did not answer an offer in time.",
					HIGH),
			Definition.alert(Keys.ALERT_BOOKING_UNASSIGNED, "Booking starting soon without a partner",
					"A confirmed booking starts within the warning time and has no partner (once per booking).", HIGH),
			Definition.alert(Keys.ALERT_PAYMENT_FAILED, "Online payment failed", "An online payment failed.", HIGH),
			Definition.alert(Keys.ALERT_CASH_DISPUTED, "Cash disputed", "A cash collection was disputed.", HIGH),
			Definition.alert(Keys.ALERT_PAYOUT_FAILED, "Payout failed", "A transfer to a partner failed.", HIGH),
			Definition.alert(Keys.ALERT_TICKET_OPENED, "Support ticket opened",
					"A ticket was opened. Normal: urgent and high-priority tickets still come as High.", NORMAL),
			Definition.alert(Keys.ALERT_TICKET_RAISED, "Ticket raised to urgent or high",
					"A ticket's priority was raised to Urgent or High.", HIGH),
			Definition.alert(Keys.ALERT_TICKET_REPLY, "Requester replied",
					"A requester replied to or reopened a ticket (the assignee, else the support staff).", NORMAL),
			Definition.alert(Keys.ALERT_LOW_RATING, "Low partner rating", "A customer rated a partner 2 or less.",
					NORMAL));

	static final Map<String, Definition> BY_KEY = DEFINITIONS.stream()
			.collect(Collectors.toMap(Definition::key, Function.identity()));

	private final NexHomeAppSettingRepository settingRepository;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditRecorder auditRecorder;
	private final NexHomeAuditUsers auditUsers;

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeSettingResponse> getSettings() {
		Map<String, NexHomeAppSetting> stored = storedSettings();
		Set<Long> editors = stored.values().stream().map(NexHomeAppSetting::getUpdatedBy).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, NexHomeAuditUsers.Person> people = editors.isEmpty() ? Map.of() : auditUsers.people(editors);
		return DEFINITIONS.stream().map(definition -> {
			NexHomeAppSetting setting = stored.get(definition.key());
			Long by = setting == null ? null : setting.getUpdatedBy();
			NexHomeAuditUsers.Person person = by == null ? null : people.get(by);
			return new NexHomeSettingResponse(definition.key(), definition.group(), definition.type(),
					definition.label(), definition.description(), valueOf(definition, setting),
					definition.defaultValue(), definition.min(), definition.max(), definition.maxLength(),
					ALERT.equals(definition.type()) ? ALERT_LEVELS : null, definition.isPublic(), by,
					person == null ? null : person.name(), setting == null ? null : setting.getUpdatedDate());
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, String> getPublicSettings() {
		Map<String, NexHomeAppSetting> stored = storedSettings();
		Map<String, String> values = new LinkedHashMap<>();
		DEFINITIONS.stream().filter(Definition::isPublic)
				.forEach(definition -> values.put(definition.key(), valueOf(definition, stored.get(definition.key()))));
		return values;
	}

	@Override
	@Transactional
	public List<NexHomeSettingResponse> updateSettings(NexHomeSettingsUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		// Every change is checked before any is written
		Map<String, String> changes = new LinkedHashMap<>();
		Set<String> seen = new HashSet<>();
		for (NexHomeSettingsUpdateRequest.Change change : request.settings()) {
			String key = change.settingKey().trim();
			Definition definition = BY_KEY.get(key);
			if (definition == null) {
				throw new InvalidRequestException("Unknown setting " + key + ".");
			}
			if (!seen.add(key)) {
				throw new InvalidRequestException("Setting " + key + " is given more than once.");
			}
			changes.put(key, checked(definition, change.value()));
		}

		Map<String, NexHomeAppSetting> stored = storedSettings();
		changes.forEach((key, value) -> {
			NexHomeAppSetting setting = stored.get(key);
			String previous = valueOf(BY_KEY.get(key), setting);
			if (previous.equals(value)) {
				return;
			}
			if (setting == null) {
				setting = new NexHomeAppSetting();
				setting.setSettingKey(key);
				setting.setCreatedBy(actingUserId);
				setting.setSettingValue(value);
				setting.setUpdatedBy(actingUserId);
				settingRepository.save(setting);
			}
			else {
				setting.setSettingValue(value);
				setting.setUpdatedBy(actingUserId);
			}
			auditRecorder.record("SETTING_UPDATED", "SETTING", key, Map.of("value", previous), Map.of("value", value),
					actingUserId);
			log.info("Setting {} changed by User ID {}", key, actingUserId);
		});
		settingRepository.flush();
		return getSettings();
	}

	@Override
	@Transactional(readOnly = true)
	public int intValue(String settingKey) {
		Definition definition = BY_KEY.get(settingKey);
		String value = current(definition);
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return Integer.parseInt(definition.defaultValue());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal decimalValue(String settingKey) {
		Definition definition = BY_KEY.get(settingKey);
		try {
			return new BigDecimal(current(definition));
		}
		catch (NumberFormatException e) {
			return new BigDecimal(definition.defaultValue());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public String alertLevel(String alertKey) {
		Definition definition = BY_KEY.get(alertKey);
		String value = current(definition);
		return ALERT_LEVELS.contains(value) ? value : definition.defaultValue();
	}

	/** The stored value of a setting, else its default. */
	private String current(Definition definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Unknown setting");
		}
		return valueOf(definition, settingRepository.findBySettingKey(definition.key()).orElse(null));
	}

	private Map<String, NexHomeAppSetting> storedSettings() {
		return settingRepository.findAll().stream()
				.collect(Collectors.toMap(NexHomeAppSetting::getSettingKey, Function.identity()));
	}

	private static String valueOf(Definition definition, NexHomeAppSetting setting) {
		return setting == null ? definition.defaultValue() : setting.getSettingValue();
	}

	/** The value as stored (trimmed, numbers normalised), or 400 naming the setting. */
	static String checked(Definition definition, String raw) {
		String value = raw.trim();
		String label = definition.label();
		switch (definition.type()) {
			case NUMBER, DECIMAL -> {
				BigDecimal number;
				try {
					number = new BigDecimal(value);
				}
				catch (NumberFormatException e) {
					throw new InvalidRequestException(label + " must be a number.");
				}
				if (NUMBER.equals(definition.type()) && number.stripTrailingZeros().scale() > 0) {
					throw new InvalidRequestException(label + " must be a whole number.");
				}
				if (DECIMAL.equals(definition.type()) && number.stripTrailingZeros().scale() > 2) {
					throw new InvalidRequestException(label + " can have at most 2 decimal places.");
				}
				if (number.doubleValue() < definition.min() || number.doubleValue() > definition.max()) {
					throw new InvalidRequestException(label + " must be between " + plain(definition.min()) + " and "
							+ plain(definition.max()) + ".");
				}
				return number.stripTrailingZeros().toPlainString();
			}
			case ALERT -> {
				String level = value.toUpperCase();
				if (!ALERT_LEVELS.contains(level)) {
					throw new InvalidRequestException(label + " must be one of: OFF, NORMAL, HIGH.");
				}
				return level;
			}
			default -> {
				if (value.isEmpty()) {
					throw new InvalidRequestException(label + " cannot be blank.");
				}
				if (value.length() > definition.maxLength()) {
					throw new InvalidRequestException(label + " must be at most " + definition.maxLength()
							+ " characters.");
				}
				if (PHONE.equals(definition.type()) && !PHONE_TEXT.matcher(value).matches()) {
					throw new InvalidRequestException(label + " must be a phone number: digits, spaces or hyphens.");
				}
				if (DIGITS.equals(definition.type()) && !DIGITS_ONLY.matcher(value).matches()) {
					throw new InvalidRequestException(label + " must be 10 to 15 digits with the country code.");
				}
				if (EMAIL.equals(definition.type()) && !EMAIL_ADDRESS.matcher(value).matches()) {
					throw new InvalidRequestException(label + " must be an email address.");
				}
				return value.replaceAll("\\s+", " ");
			}
		}
	}

	private static String plain(Double number) {
		return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
	}
}
