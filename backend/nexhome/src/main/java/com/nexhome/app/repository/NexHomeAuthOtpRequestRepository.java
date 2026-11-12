package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAuthOtpRequest;

@Repository
public interface NexHomeAuthOtpRequestRepository extends JpaRepository<NexHomeAuthOtpRequest, Long>,
		JpaSpecificationExecutor<NexHomeAuthOtpRequest> {

	/** Who looks at the Login OTPs screen: a super user, an admin, or other staff (sub-admins). */
	enum OtpViewer { SUPER_USER, ADMIN, OTHER }

	/**
	 * The Login OTPs screen (V61, V65): LOGIN / REGISTRATION OTPs by the app that asked ({@code requestedFrom}):
	 * <ul>
	 * <li>the customer or partner app: every viewer, whatever other roles the account holds;</li>
	 * <li>the admin console (STAFF): super users all; admins those of accounts without SUPER_USER / ADMIN (sub-admins);
	 * other staff none;</li>
	 * <li>older OTPs without the app: super users all; admins those of accounts without SUPER_USER / ADMIN; other
	 * staff those of accounts without any staff role.</li>
	 * </ul>
	 * Optionally those whose number contains {@code search} (digits).
	 */
	static Specification<NexHomeAuthOtpRequest> sharedOtpSpec(String search, OtpViewer viewer) {
		Specification<NexHomeAuthOtpRequest> spec = (otp, query, cb) -> {
			var base = cb.and(cb.isNotNull(otp.get("user")),
					otp.get("purpose").in(java.util.List.of("LOGIN", "REGISTRATION")));
			if (viewer == OtpViewer.SUPER_USER) {
				return base;
			}
			// Whether the account holds or held one of the roles
			java.util.function.Function<java.util.List<String>, jakarta.persistence.criteria.Predicate> holds = codes -> {
				var sub = query.subquery(Long.class);
				var userRole = sub.from(com.nexhome.app.model.NexHomeUserRole.class);
				sub.select(userRole.get("user").get("id")).where(
						cb.equal(userRole.get("user").get("id"), otp.get("user").get("id")),
						userRole.get("role").get("code").in(codes));
				return cb.exists(sub);
			};
			var fromApps = otp.get("requestedFrom").in(java.util.List.of("CUSTOMER", "PARTNER"));
			var top = java.util.List.of(com.nexhome.app.model.NexHomeRole.SUPER_USER,
					com.nexhome.app.model.NexHomeRole.ADMIN);
			if (viewer == OtpViewer.ADMIN) {
				var console = cb.and(cb.equal(otp.get("requestedFrom"), "STAFF"), cb.not(holds.apply(top)));
				var older = cb.and(cb.isNull(otp.get("requestedFrom")), cb.not(holds.apply(top)));
				return cb.and(base, cb.or(fromApps, console, older));
			}
			var older = cb.and(cb.isNull(otp.get("requestedFrom")), cb.not(holds.apply(java.util.List.of(
					com.nexhome.app.model.NexHomeRole.SUPER_USER, com.nexhome.app.model.NexHomeRole.ADMIN,
					com.nexhome.app.model.NexHomeRole.SUB_ADMIN))));
			return cb.and(base, cb.or(fromApps, older));
		};
		if (search == null) {
			return spec;
		}
		String pattern = NexHomeSearch.containsPattern(search);
		return spec.and((otp, query, cb) -> cb.or(cb.like(otp.get("mobileNumber"), pattern, '\\'),
				cb.like(cb.concat(otp.get("countryCode"), otp.get("mobileNumber")), pattern, '\\')));
	}

	/** The latest OTP of any status, used for the resend cooldown. */
	Optional<NexHomeAuthOtpRequest> findFirstByCountryCodeAndMobileNumberAndPurposeOrderByCreationDateDesc(
			String countryCode, String mobileNumber, String purpose);

	/** The pending OTP; unique by the partial index ux_nexhome_auth_otp_requests_one_pending. */
	Optional<NexHomeAuthOtpRequest> findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(String countryCode,
			String mobileNumber, String purpose, String requestStatus);
}
