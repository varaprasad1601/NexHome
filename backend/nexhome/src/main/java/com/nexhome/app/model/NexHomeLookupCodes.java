package com.nexhome.app.model;

/**
 * Lookup type codes and the value codes that application logic depends on. The values themselves live in
 * nexhome_lookup_values (seeded by Flyway); business columns store these codes as plain text.
 */
public final class NexHomeLookupCodes {

	private NexHomeLookupCodes() {
	}

	/** Lookup type codes. */
	public static final class Types {

		public static final String ACCOUNT_STATUS = "ACCOUNT_STATUS";
		public static final String FUNCTION_TYPE = "FUNCTION_TYPE";
		public static final String OTP_PURPOSE = "OTP_PURPOSE";
		public static final String OTP_REQUEST_STATUS = "OTP_REQUEST_STATUS";
		public static final String ADDRESS_TYPE = "ADDRESS_TYPE";
		public static final String PARTNER_ONBOARDING_STATUS = "PARTNER_ONBOARDING_STATUS";
		public static final String VERIFICATION_STATUS = "VERIFICATION_STATUS";
		public static final String PARTNER_AVAILABILITY_STATUS = "PARTNER_AVAILABILITY_STATUS";
		public static final String DOCUMENT_TYPE = "DOCUMENT_TYPE";
		public static final String PREFERRED_CONTACT_LANGUAGE = "PREFERRED_CONTACT_LANGUAGE";
		public static final String SKILL_CATEGORY = "SKILL_CATEGORY";
		public static final String PREFERRED_ARRANGEMENT = "PREFERRED_ARRANGEMENT";
		public static final String PAYMENT_BASIS = "PAYMENT_BASIS";
		public static final String WEEKEND_AVAILABILITY = "WEEKEND_AVAILABILITY";
		public static final String EMERGENCY_CONTACT_RELATIONSHIP = "EMERGENCY_CONTACT_RELATIONSHIP";
		public static final String PRICING_TYPE = "PRICING_TYPE";
		public static final String COMMISSION_TYPE = "COMMISSION_TYPE";
		public static final String MEDIA_TYPE = "MEDIA_TYPE";
		public static final String REQUIREMENT_TYPE = "REQUIREMENT_TYPE";
		/** What a service works on; each value's metadata lists the pricing types it allows. */
		public static final String SERVICE_TARGET = "SERVICE_TARGET";
		/** The options of a service's customer requirements. */
		public static final String CUSTOMER_REQUIREMENT = "CUSTOMER_REQUIREMENT";
		/** The options of a service's partner requirements. */
		public static final String PARTNER_REQUIREMENT = "PARTNER_REQUIREMENT";
		public static final String FAQ_AUDIENCE = "FAQ_AUDIENCE";
		public static final String POLICY_AUDIENCE = "POLICY_AUDIENCE";
		public static final String ACCEPTANCE_METHOD = "ACCEPTANCE_METHOD";
		/** The daily slots of a partner's weekly availability grid. */
		public static final String AVAILABILITY_SLOT = "AVAILABILITY_SLOT";
		/** Fees added to bookings; value ONLINE, metadata.percentage (V35). */
		public static final String TRANSACTION_FEE = "TRANSACTION_FEE";
		/** The kinds of government ID (V19). */
		public static final String GOVERNMENT_ID_TYPE = "GOVERNMENT_ID_TYPE";
		/** A partner slot reservation's status (Module 5). */
		public static final String SLOT_RESERVATION_STATUS = "SLOT_RESERVATION_STATUS";
		/** Module 6. */
		public static final String CART_STATUS = "CART_STATUS";
		public static final String BOOKING_TYPE = "BOOKING_TYPE";
		public static final String BOOKING_STATUS = "BOOKING_STATUS";
		public static final String BOOKING_PAYMENT_STATUS = "BOOKING_PAYMENT_STATUS";
		public static final String PAYMENT_METHOD = "PAYMENT_METHOD";
		public static final String ASSIGNMENT_STATUS = "ASSIGNMENT_STATUS";
		public static final String BOOKING_TIMER_EVENT = "BOOKING_TIMER_EVENT";
		/** Module 7. */
		public static final String PAYMENT_ORDER_STATUS = "PAYMENT_ORDER_STATUS";
		public static final String PAYMENT_STATUS = "PAYMENT_STATUS";
		public static final String WEBHOOK_PROCESSING_STATUS = "WEBHOOK_PROCESSING_STATUS";
		public static final String REFUND_STATUS = "REFUND_STATUS";
		public static final String PAYOUT_STATUS = "PAYOUT_STATUS";
		public static final String TRANSFER_STATUS = "TRANSFER_STATUS";
		public static final String CASH_COLLECTION_STATUS = "CASH_COLLECTION_STATUS";
		/** Module 9. */
		public static final String REVIEW_STATUS = "REVIEW_STATUS";
		public static final String SUPPORT_CATEGORY = "SUPPORT_CATEGORY";
		public static final String SUPPORT_PRIORITY = "SUPPORT_PRIORITY";
		public static final String SUPPORT_TICKET_STATUS = "SUPPORT_TICKET_STATUS";
		public static final String NOTIFICATION_TYPE = "NOTIFICATION_TYPE";
		public static final String NOTIFICATION_CHANNEL = "NOTIFICATION_CHANNEL";
		public static final String DELIVERY_STATUS = "DELIVERY_STATUS";

		private Types() {
		}
	}

	/** Values of {@link Types#ACCOUNT_STATUS}; only ACTIVE accounts can sign in. */
	public static final class AccountStatus {

		public static final String ACTIVE = "ACTIVE";
		public static final String SUSPENDED = "SUSPENDED";
		public static final String BLOCKED = "BLOCKED";
		public static final String DEACTIVATED = "DEACTIVATED";

		private AccountStatus() {
		}
	}

	/** Values of {@link Types#DOCUMENT_TYPE} that code depends on. */
	public static final class DocumentType {

		/** Needs an ID type (GOVERNMENT_ID_TYPE). */
		public static final String GOVERNMENT_ID = "GOVERNMENT_ID";
		/** A skill certificate; needs its name. */
		public static final String CERTIFICATION = "CERTIFICATION";

		private DocumentType() {
		}
	}

	/** Values of {@link Types#OTP_PURPOSE}. */
	public static final class OtpPurpose {

		public static final String LOGIN = "LOGIN";
		public static final String REGISTRATION = "REGISTRATION";
		public static final String PHONE_CHANGE = "PHONE_CHANGE";
		public static final String SERVICE_START = "SERVICE_START";
		public static final String SERVICE_COMPLETION = "SERVICE_COMPLETION";

		private OtpPurpose() {
		}
	}

	/** Values of {@link Types#OTP_REQUEST_STATUS}. */
	public static final class OtpRequestStatus {

		public static final String PENDING = "PENDING";
		public static final String VERIFIED = "VERIFIED";
		public static final String EXPIRED = "EXPIRED";
		public static final String LOCKED = "LOCKED";
		public static final String FAILED = "FAILED";

		private OtpRequestStatus() {
		}
	}

	/**
	 * Values of {@link Types#PARTNER_ONBOARDING_STATUS} (V18). A partner account's profile starts NOT_STARTED, becomes a
	 * DRAFT ("Profile Draft") once saved, is submitted for review (UNDER_REVIEW), and a reviewer approves it, rejects it
	 * or returns it for more information or documents (CHANGES_REQUESTED), after which the partner submits again.
	 */
	public static final class PartnerOnboardingStatus {

		public static final String NOT_STARTED = "NOT_STARTED";
		public static final String DRAFT = "DRAFT";
		public static final String UNDER_REVIEW = "UNDER_REVIEW";
		public static final String CHANGES_REQUESTED = "CHANGES_REQUESTED";
		public static final String APPROVED = "APPROVED";
		public static final String REJECTED = "REJECTED";

		private PartnerOnboardingStatus() {
		}
	}

	/** Values of {@link Types#VERIFICATION_STATUS} (partner profiles, documents and bank accounts). */
	public static final class VerificationStatus {

		public static final String PENDING = "PENDING";
		public static final String IN_REVIEW = "IN_REVIEW";
		public static final String VERIFIED = "VERIFIED";
		public static final String REJECTED = "REJECTED";
		public static final String EXPIRED = "EXPIRED";

		private VerificationStatus() {
		}
	}

	/** Values of {@link Types#PARTNER_AVAILABILITY_STATUS}. */
	public static final class PartnerAvailabilityStatus {

		public static final String ONLINE = "ONLINE";
		public static final String OFFLINE = "OFFLINE";
		public static final String BUSY = "BUSY";

		private PartnerAvailabilityStatus() {
		}
	}

	/** Values of {@link Types#WEEKEND_AVAILABILITY}; checked against the weekly availability grid. */
	public static final class WeekendAvailability {

		public static final String SATURDAY = "SATURDAY";
		public static final String SUNDAY = "SUNDAY";
		public static final String NEITHER = "NEITHER";
		public static final String VARIES = "VARIES";

		private WeekendAvailability() {
		}
	}

	/** Values of {@link Types#PRICING_TYPE}: how a service's base price is applied. */
	public static final class PricingType {

		/** One price for the job. */
		public static final String FIXED = "FIXED";
		/** The base price is per hour, billed from a minimum in increments. */
		public static final String HOURLY = "HOURLY";
		/** Priced by the size of the home: each variant is a size (e.g. 2 BHK), charged once. */
		public static final String AREA = "AREA";
		/** The base price is an inspection fee; the work is quoted on site. */
		public static final String INSPECTION = "INSPECTION";

		private PricingType() {
		}
	}

	/** Whom a service requirement is for ({@code nexhome_service_requirements.requirement_for}, checked there). */
	public static final class RequirementFor {

		public static final String CUSTOMER = "CUSTOMER";
		public static final String PARTNER = "PARTNER";

		private RequirementFor() {
		}
	}

	/** Values of {@link Types#COMMISSION_TYPE}. */
	public static final class CommissionType {

		public static final String PERCENTAGE = "PERCENTAGE";
		public static final String FIXED = "FIXED";

		private CommissionType() {
		}
	}

	/** Values of {@link Types#SLOT_RESERVATION_STATUS}: HELD and CONFIRMED block the partner's time. */
	public static final class SlotReservationStatus {

		public static final String HELD = "HELD";
		public static final String CONFIRMED = "CONFIRMED";
		public static final String RELEASED = "RELEASED";
		public static final String EXPIRED = "EXPIRED";
		public static final String COMPLETED = "COMPLETED";

		private SlotReservationStatus() {
		}
	}

	/** Values of {@link Types#CART_STATUS}: one ACTIVE cart per customer. */
	public static final class CartStatus {

		public static final String ACTIVE = "ACTIVE";
		public static final String CHECKED_OUT = "CHECKED_OUT";
		public static final String ABANDONED = "ABANDONED";

		private CartStatus() {
		}
	}

	/** Values of {@link Types#BOOKING_TYPE}. */
	public static final class BookingType {

		public static final String INSTANT = "INSTANT";
		public static final String SCHEDULED = "SCHEDULED";

		private BookingType() {
		}
	}

	/** Values of {@link Types#BOOKING_STATUS}. */
	public static final class BookingStatus {

		public static final String PENDING = "PENDING";
		public static final String CONFIRMED = "CONFIRMED";
		public static final String ASSIGNED = "ASSIGNED";
		public static final String PARTNER_EN_ROUTE = "PARTNER_EN_ROUTE";
		public static final String IN_PROGRESS = "IN_PROGRESS";
		public static final String PAUSED = "PAUSED";
		public static final String COMPLETED = "COMPLETED";
		public static final String CANCELLED = "CANCELLED";

		private BookingStatus() {
		}
	}

	/** Values of {@link Types#BOOKING_PAYMENT_STATUS}. */
	public static final class BookingPaymentStatus {

		public static final String PENDING = "PENDING";
		public static final String AUTHORIZED = "AUTHORIZED";
		public static final String PAID = "PAID";
		public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
		public static final String REFUNDED = "REFUNDED";
		public static final String FAILED = "FAILED";

		private BookingPaymentStatus() {
		}
	}

	/** Values of {@link Types#PAYMENT_METHOD}. */
	public static final class PaymentMethod {

		public static final String ONLINE = "ONLINE";
		public static final String CASH = "CASH";

		private PaymentMethod() {
		}
	}

	/** Values of {@link Types#ASSIGNMENT_STATUS}. */
	public static final class AssignmentStatus {

		public static final String OFFERED = "OFFERED";
		/** Given to the partner by staff, waiting for the partner to accept (V36). */
		public static final String ASSIGNED = "ASSIGNED";
		public static final String ACCEPTED = "ACCEPTED";
		/** "Started Working": the timer started (V36). */
		public static final String STARTED = "STARTED";
		public static final String REJECTED = "REJECTED";
		public static final String CANCELLED = "CANCELLED";
		public static final String EXPIRED = "EXPIRED";
		public static final String COMPLETED = "COMPLETED";

		private AssignmentStatus() {
		}
	}

	/** Values of {@link Types#BOOKING_TIMER_EVENT}. */
	public static final class BookingTimerEvent {

		public static final String START = "START";
		public static final String PAUSE = "PAUSE";
		public static final String RESUME = "RESUME";
		public static final String STOP = "STOP";

		private BookingTimerEvent() {
		}
	}

	/** Values of {@link Types#PAYMENT_ORDER_STATUS} (Module 7). */
	public static final class PaymentOrderStatus {

		public static final String CREATED = "CREATED";
		public static final String PENDING = "PENDING";
		public static final String AUTHORIZED = "AUTHORIZED";
		public static final String PAID = "PAID";
		public static final String FAILED = "FAILED";
		public static final String EXPIRED = "EXPIRED";
		public static final String CANCELLED = "CANCELLED";

		private PaymentOrderStatus() {
		}
	}

	/** Values of {@link Types#PAYMENT_STATUS} (Module 7). */
	public static final class PaymentStatus {

		public static final String PENDING = "PENDING";
		public static final String AUTHORIZED = "AUTHORIZED";
		public static final String CAPTURED = "CAPTURED";
		public static final String FAILED = "FAILED";
		public static final String CANCELLED = "CANCELLED";
		public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
		public static final String REFUNDED = "REFUNDED";

		private PaymentStatus() {
		}
	}

	/** Values of {@link Types#WEBHOOK_PROCESSING_STATUS} (Module 7). */
	public static final class WebhookProcessingStatus {

		public static final String PENDING = "PENDING";
		public static final String PROCESSING = "PROCESSING";
		public static final String PROCESSED = "PROCESSED";
		public static final String FAILED = "FAILED";
		public static final String IGNORED = "IGNORED";

		private WebhookProcessingStatus() {
		}
	}

	/** Values of {@link Types#REFUND_STATUS} (Module 7). */
	public static final class RefundStatus {

		public static final String REQUESTED = "REQUESTED";
		public static final String PROCESSING = "PROCESSING";
		public static final String SUCCEEDED = "SUCCEEDED";
		public static final String FAILED = "FAILED";
		public static final String CANCELLED = "CANCELLED";

		private RefundStatus() {
		}
	}

	/** Values of {@link Types#PAYOUT_STATUS} (Module 7). */
	public static final class PayoutStatus {

		public static final String PENDING = "PENDING";
		public static final String PROCESSING = "PROCESSING";
		public static final String PAID = "PAID";
		public static final String FAILED = "FAILED";
		public static final String ON_HOLD = "ON_HOLD";
		public static final String CANCELLED = "CANCELLED";

		private PayoutStatus() {
		}
	}

	/** Values of {@link Types#TRANSFER_STATUS} (Module 7). */
	public static final class TransferStatus {

		public static final String CREATED = "CREATED";
		public static final String PROCESSING = "PROCESSING";
		public static final String SETTLED = "SETTLED";
		public static final String FAILED = "FAILED";
		public static final String REVERSED = "REVERSED";

		private TransferStatus() {
		}
	}

	/** Values of {@link Types#CASH_COLLECTION_STATUS} (Module 7). */
	public static final class CashCollectionStatus {

		public static final String DUE = "DUE";
		public static final String COLLECTED = "COLLECTED";
		public static final String DISPUTED = "DISPUTED";
		public static final String WAIVED = "WAIVED";

		private CashCollectionStatus() {
		}
	}

	/** Values of {@link Types#REVIEW_STATUS} (Module 9); only PUBLISHED reviews count towards ratings. */
	public static final class ReviewStatus {

		public static final String PUBLISHED = "PUBLISHED";
		public static final String HIDDEN = "HIDDEN";
		public static final String UNDER_REVIEW = "UNDER_REVIEW";

		private ReviewStatus() {
		}
	}

	/** Values of {@link Types#SUPPORT_TICKET_STATUS} (Module 9). */
	public static final class SupportTicketStatus {

		public static final String OPEN = "OPEN";
		public static final String ASSIGNED = "ASSIGNED";
		public static final String IN_PROGRESS = "IN_PROGRESS";
		public static final String RESOLVED = "RESOLVED";
		public static final String CLOSED = "CLOSED";

		private SupportTicketStatus() {
		}
	}

	/** Values of {@link Types#SUPPORT_PRIORITY} (Module 9). */
	public static final class SupportPriority {

		public static final String MEDIUM = "MEDIUM";

		private SupportPriority() {
		}
	}

	/** Values of {@link Types#NOTIFICATION_TYPE} (V41). */
	public static final class NotificationType {

		public static final String BOOKING = "BOOKING";
		public static final String PAYMENT = "PAYMENT";
		public static final String REVIEW = "REVIEW";
		public static final String SUPPORT = "SUPPORT";
		public static final String ONBOARDING = "ONBOARDING";
		public static final String ACCOUNT = "ACCOUNT";
		public static final String ANNOUNCEMENT = "ANNOUNCEMENT";

		private NotificationType() {
		}
	}

	/** Values of {@link Types#NOTIFICATION_CHANNEL}; IN_APP added by V41 (no push, SMS or email provider yet). */
	public static final class NotificationChannel {

		public static final String IN_APP = "IN_APP";

		private NotificationChannel() {
		}
	}

	/** Values of {@link Types#DELIVERY_STATUS} (Module 9). */
	public static final class DeliveryStatus {

		public static final String PENDING = "PENDING";
		public static final String SENT = "SENT";
		public static final String DELIVERED = "DELIVERED";
		public static final String FAILED = "FAILED";

		private DeliveryStatus() {
		}
	}

	/** Values of {@link Types#FAQ_AUDIENCE} and {@link Types#POLICY_AUDIENCE}; BOTH applies to everyone. */
	public static final class Audience {

		public static final String CUSTOMER = "CUSTOMER";
		public static final String PARTNER = "PARTNER";
		public static final String BOTH = "BOTH";

		private Audience() {
		}
	}
}
