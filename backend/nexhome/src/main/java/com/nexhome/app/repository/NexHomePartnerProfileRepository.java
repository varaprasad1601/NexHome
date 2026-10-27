package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerProfile;

@Repository
public interface NexHomePartnerProfileRepository extends JpaRepository<NexHomePartnerProfile, Long>,
		JpaSpecificationExecutor<NexHomePartnerProfile> {

	/** user_id is unique: one partner profile per account. */
	Optional<NexHomePartnerProfile> findByUserId(Long userId);

	/** The profiles of a page of users, with their main service category, in one query. */
	@EntityGraph(attributePaths = "mainServiceCategory")
	List<NexHomePartnerProfile> findByUserIdIn(Collection<Long> userIds);

	boolean existsByUserId(Long userId);

	boolean existsByMainServiceCategoryId(Long categoryId);

	/**
	 * Partner profiles matching every filter given: statuses (exact codes), city (lower-cased, matched
	 * case-insensitively) and {@code search} (lower-cased; part of the name or public id). Null filters are left out
	 * of the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomePartnerProfile> search(String onboardingStatus, String verificationStatus,
			String availabilityStatus, String city, String search, Pageable pageable) {
		Specification<NexHomePartnerProfile> spec = Specification.unrestricted();
		if (onboardingStatus != null) {
			spec = spec.and((profile, query, cb) -> cb.equal(profile.get("onboardingStatus"), onboardingStatus));
		}
		if (verificationStatus != null) {
			spec = spec.and((profile, query, cb) -> cb.equal(profile.get("verificationStatus"), verificationStatus));
		}
		if (availabilityStatus != null) {
			spec = spec.and((profile, query, cb) -> cb.equal(profile.get("availabilityStatus"), availabilityStatus));
		}
		if (city != null) {
			spec = spec.and((profile, query, cb) -> cb.equal(cb.lower(profile.get("city")), city));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			Specification<NexHomePartnerProfile> middleName = (profile, query, cb) -> cb.like(
					cb.lower(profile.get("middleName")), pattern, '\\');
			spec = spec.and(NexHomeSearch.<NexHomePartnerProfile>nameOrPublicIdContains(search).or(middleName));
		}
		return findAll(spec, pageable);
	}
}
