package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeCustomerProfile;

@Repository
public interface NexHomeCustomerProfileRepository extends JpaRepository<NexHomeCustomerProfile, Long>,
		JpaSpecificationExecutor<NexHomeCustomerProfile> {

	/** user_id is unique: one customer profile per account. */
	Optional<NexHomeCustomerProfile> findByUserId(Long userId);

	boolean existsByUserId(Long userId);

	/** The profiles of a page of users, in one query. */
	List<NexHomeCustomerProfile> findByUserIdIn(Collection<Long> userIds);

	/**
	 * All customer profiles, or those whose name or public id contains {@code search} (lower-cased). A null filter is
	 * left out of the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomeCustomerProfile> search(String search, Pageable pageable) {
		Specification<NexHomeCustomerProfile> spec = Specification.unrestricted();
		if (search != null) {
			spec = spec.and(NexHomeSearch.nameOrPublicIdContains(search));
		}
		return findAll(spec, pageable);
	}
}
