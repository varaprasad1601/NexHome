package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAdminProfile;

@Repository
public interface NexHomeAdminProfileRepository extends JpaRepository<NexHomeAdminProfile, Long>,
		JpaSpecificationExecutor<NexHomeAdminProfile> {

	/** user_id is unique: one staff profile per account. */
	Optional<NexHomeAdminProfile> findByUserId(Long userId);

	boolean existsByUserId(Long userId);

	/** Employee codes are stored upper-cased and are unique when present. */
	Optional<NexHomeAdminProfile> findByEmployeeCode(String employeeCode);

	/**
	 * All staff profiles, or those whose name, public id or employee code contains {@code search} (lower-cased). A
	 * null filter is left out of the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomeAdminProfile> search(String search, Pageable pageable) {
		Specification<NexHomeAdminProfile> spec = Specification.unrestricted();
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			Specification<NexHomeAdminProfile> employeeCode = (profile, query, cb) -> cb.like(
					cb.lower(profile.get("employeeCode")), pattern, '\\');
			spec = spec.and(NexHomeSearch.<NexHomeAdminProfile>nameOrPublicIdContains(search).or(employeeCode));
		}
		return findAll(spec, pageable);
	}
}
