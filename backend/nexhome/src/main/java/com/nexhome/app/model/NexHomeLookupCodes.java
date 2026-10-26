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
		/** The daily slots of a partner's weekly availability grid. */
		public static final String AVAILABILITY_SLOT = "AVAILABILITY_SLOT";

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
	 * Values of {@link Types#PARTNER_ONBOARDING_STATUS}. A profile starts as DRAFT, is submitted for review
	 * (UNDER_REVIEW), and a reviewer approves it, rejects it, asks for changes (PROFILE_INCOMPLETE) or training.
	 */
	public static final class PartnerOnboardingStatus {

		public static final String DRAFT = "DRAFT";
		public static final String PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
		public static final String UNDER_REVIEW = "UNDER_REVIEW";
		public static final String TRAINING_REQUIRED = "TRAINING_REQUIRED";
		public static final String APPROVED = "APPROVED";
		public static final String REJECTED = "REJECTED";
		public static final String SUSPENDED = "SUSPENDED";

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
}
