package com.nexhome.app.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public interface NexHomeUserRepository extends JpaRepository<NexHomeUser, Long>,
		JpaSpecificationExecutor<NexHomeUser> {

	/** (country_code, mobile_number) is unique: one account per mobile number. */
	Optional<NexHomeUser> findByCountryCodeAndMobileNumber(String countryCode, String mobileNumber);

	/** Emails are stored lower-cased and are unique when present. */
	Optional<NexHomeUser> findByEmail(String email);

	/**
	 * Users who currently hold the role (active assignment), optionally only with one account status and/or whose
	 * mobile number or email contains {@code search}. A null filter is left out of the SQL altogether: binding null
	 * to an {@code :x is null or ...} condition fails on PostgreSQL once the driver server-prepares the statement
	 * (the null is then typed bytea).
	 */
	default Page<NexHomeUser> findByActiveRole(String roleCode, String accountStatus, String search,
			Pageable pageable) {
		Specification<NexHomeUser> spec = (user, query, cb) -> {
			Subquery<Integer> holdsRole = query.subquery(Integer.class);
			Root<NexHomeUserRole> userRole = holdsRole.from(NexHomeUserRole.class);
			holdsRole.select(cb.literal(1)).where(
					cb.equal(userRole.get("user"), user),
					cb.isTrue(userRole.get("active")),
					cb.equal(userRole.get("role").get("code"), roleCode));
			return cb.exists(holdsRole);
		};
		if (accountStatus != null) {
			spec = spec.and((user, query, cb) -> cb.equal(user.get("accountStatus"), accountStatus));
		}
		if (search != null) {
			String pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			spec = spec.and((user, query, cb) -> cb.or(
					cb.like(user.get("mobileNumber"), pattern, '\\'),
					cb.like(user.get("email"), pattern, '\\')));
		}
		return findAll(spec, pageable);
	}

	/**
	 * Staff accounts: users who currently hold (active assignment of an active role) one of {@code staffRoles}, and
	 * without {@code hiddenRole} when it is given (super users are hidden from other staff). Optional filters: a role
	 * they hold, an account status, and {@code search} (lower-cased) contained in the mobile number, email, staff
	 * profile name (first, last or "first last") or profile public id. Null filters are left out of the SQL; see
	 * {@link #findByActiveRole}.
	 */
	static Specification<NexHomeUser> staffSpec(Collection<String> staffRoles, String hiddenRole, String roleCode,
			String accountStatus, String search) {
		Specification<NexHomeUser> spec = holdsActiveRole(staffRoles, true);
		if (hiddenRole != null) {
			spec = spec.and(holdsActiveRole(List.of(hiddenRole), false));
		}
		if (roleCode != null) {
			spec = spec.and(holdsActiveRole(List.of(roleCode), true));
		}
		if (accountStatus != null) {
			spec = spec.and((user, query, cb) -> cb.equal(user.get("accountStatus"), accountStatus));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((user, query, cb) -> {
				Subquery<Integer> profileMatches = query.subquery(Integer.class);
				Root<NexHomeAdminProfile> profile = profileMatches.from(NexHomeAdminProfile.class);
				Expression<String> fullName = cb.concat(cb.concat(cb.lower(profile.get("firstName")), " "),
						cb.lower(profile.get("lastName")));
				profileMatches.select(cb.literal(1)).where(
						cb.equal(profile.get("user"), user),
						cb.or(cb.like(fullName, pattern, '\\'),
								cb.like(cb.lower(profile.get("publicId")), pattern, '\\')));
				return cb.or(
						cb.like(user.get("mobileNumber"), pattern, '\\'),
						cb.like(user.get("email"), pattern, '\\'),
						cb.exists(profileMatches));
			});
		}
		return spec;
	}

	/**
	 * Customers: users who currently hold the CUSTOMER role (active assignment of an active role). Optional filters:
	 * an account status, and {@code search} (lower-cased) contained in the mobile number, email, customer profile name
	 * (first, last or "first last") or profile public id. Null filters are left out of the SQL; see
	 * {@link #findByActiveRole}.
	 */
	static Specification<NexHomeUser> customerSpec(String accountStatus, String search) {
		Specification<NexHomeUser> spec = holdsActiveRole(List.of(NexHomeRole.CUSTOMER), true);
		if (accountStatus != null) {
			spec = spec.and((user, query, cb) -> cb.equal(user.get("accountStatus"), accountStatus));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((user, query, cb) -> {
				Subquery<Integer> profileMatches = query.subquery(Integer.class);
				Root<NexHomeCustomerProfile> profile = profileMatches.from(NexHomeCustomerProfile.class);
				Expression<String> fullName = cb.concat(cb.concat(cb.lower(profile.get("firstName")), " "),
						cb.lower(profile.get("lastName")));
				profileMatches.select(cb.literal(1)).where(
						cb.equal(profile.get("user"), user),
						cb.or(cb.like(fullName, pattern, '\\'),
								cb.like(cb.lower(profile.get("publicId")), pattern, '\\')));
				return cb.or(
						cb.like(user.get("mobileNumber"), pattern, '\\'),
						cb.like(user.get("email"), pattern, '\\'),
						cb.exists(profileMatches));
			});
		}
		return spec;
	}

	/**
	 * Partners: users who currently hold the PARTNER role. Optional filters (null ones are left out of the SQL; see
	 * {@link #findByActiveRole}): an account status; the onboarding profile, as {@code onboardingStatuses} (a profile
	 * in one of them) and/or {@code withoutProfile} (no profile yet), either matching; its availability; and
	 * {@code search} (lower-cased) contained in the mobile number, email, profile name (first, last or "first last")
	 * or profile public id.
	 */
	static Specification<NexHomeUser> partnerSpec(String accountStatus, Collection<String> onboardingStatuses,
			boolean withoutProfile, String availabilityStatus, String search) {
		return partnerSpec(accountStatus, onboardingStatuses, withoutProfile, availabilityStatus, null, search);
	}

	/** As above, also only partners whose profile's main service category is {@code mainServiceCategoryId}. */
	static Specification<NexHomeUser> partnerSpec(String accountStatus, Collection<String> onboardingStatuses,
			boolean withoutProfile, String availabilityStatus, Long mainServiceCategoryId, String search) {
		Specification<NexHomeUser> spec = holdsActiveRole(List.of(NexHomeRole.PARTNER), true);
		if (mainServiceCategoryId != null) {
			spec = spec.and((user, query, cb) -> {
				Subquery<Integer> profiles = query.subquery(Integer.class);
				Root<NexHomePartnerProfile> profile = profiles.from(NexHomePartnerProfile.class);
				profiles.select(cb.literal(1)).where(cb.equal(profile.get("user"), user),
						cb.equal(profile.get("mainServiceCategory").get("id"), mainServiceCategoryId));
				return cb.exists(profiles);
			});
		}
		if (accountStatus != null) {
			spec = spec.and((user, query, cb) -> cb.equal(user.get("accountStatus"), accountStatus));
		}
		if (onboardingStatuses != null || withoutProfile || availabilityStatus != null) {
			spec = spec.and((user, query, cb) -> {
				List<Predicate> either = new ArrayList<>();
				if (onboardingStatuses == null || !onboardingStatuses.isEmpty() || availabilityStatus != null) {
					Subquery<Integer> profiles = query.subquery(Integer.class);
					Root<NexHomePartnerProfile> profile = profiles.from(NexHomePartnerProfile.class);
					List<Predicate> where = new ArrayList<>(List.of(cb.equal(profile.get("user"), user)));
					if (onboardingStatuses != null) {
						where.add(profile.get("onboardingStatus").in(onboardingStatuses));
					}
					if (availabilityStatus != null) {
						where.add(cb.equal(profile.get("availabilityStatus"), availabilityStatus));
					}
					profiles.select(cb.literal(1)).where(where.toArray(Predicate[]::new));
					either.add(cb.exists(profiles));
				}
				if (withoutProfile) {
					Subquery<Integer> profiles = query.subquery(Integer.class);
					Root<NexHomePartnerProfile> profile = profiles.from(NexHomePartnerProfile.class);
					profiles.select(cb.literal(1)).where(cb.equal(profile.get("user"), user));
					either.add(cb.not(cb.exists(profiles)));
				}
				return cb.or(either.toArray(Predicate[]::new));
			});
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((user, query, cb) -> {
				Subquery<Integer> profileMatches = query.subquery(Integer.class);
				Root<NexHomePartnerProfile> profile = profileMatches.from(NexHomePartnerProfile.class);
				Expression<String> fullName = cb.concat(cb.concat(cb.lower(profile.get("firstName")), " "),
						cb.lower(profile.get("lastName")));
				profileMatches.select(cb.literal(1)).where(
						cb.equal(profile.get("user"), user),
						cb.or(cb.like(fullName, pattern, '\\'),
								cb.like(cb.lower(profile.get("publicId")), pattern, '\\')));
				return cb.or(
						cb.like(user.get("mobileNumber"), pattern, '\\'),
						cb.like(user.get("email"), pattern, '\\'),
						cb.exists(profileMatches));
			});
		}
		return spec;
	}

	/** Holds (or, with {@code holds} false, does not hold) one of the roles: active assignment of an active role. */
	private static Specification<NexHomeUser> holdsActiveRole(Collection<String> roleCodes, boolean holds) {
		return (user, query, cb) -> {
			Subquery<Integer> holdsRole = query.subquery(Integer.class);
			Root<NexHomeUserRole> userRole = holdsRole.from(NexHomeUserRole.class);
			holdsRole.select(cb.literal(1)).where(
					cb.equal(userRole.get("user"), user),
					cb.isTrue(userRole.get("active")),
					cb.isTrue(userRole.get("role").get("activeFlag")),
					userRole.get("role").get("code").in(roleCodes));
			return holds ? cb.exists(holdsRole) : cb.not(cb.exists(holdsRole));
		};
	}
}
