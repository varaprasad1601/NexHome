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
}
