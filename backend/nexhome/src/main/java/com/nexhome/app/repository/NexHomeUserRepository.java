package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;

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
}
