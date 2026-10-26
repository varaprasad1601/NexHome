package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePolicyDocument;

@Repository
public interface NexHomePolicyDocumentRepository extends JpaRepository<NexHomePolicyDocument, Long>,
		JpaSpecificationExecutor<NexHomePolicyDocument> {

	/** (policy_key, version) is unique. */
	boolean existsByPolicyKeyAndVersion(String policyKey, String version);

	/**
	 * Published versions already in effect, newest effective first within each key; the first row per key is that
	 * policy's current version.
	 */
	@Query("select p from NexHomePolicyDocument p where p.published = true and p.effectiveFrom <= :now "
			+ "order by p.policyKey, p.effectiveFrom desc, p.publishedAt desc, p.id desc")
	List<NexHomePolicyDocument> findPublishedInEffect(@Param("now") Instant now);

	/**
	 * Policy versions matching every filter given: key, audience and published flag. Null filters are left out of
	 * the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomePolicyDocument> search(String policyKey, String audience, Boolean published,
			Pageable pageable) {
		Specification<NexHomePolicyDocument> spec = Specification.unrestricted();
		if (policyKey != null) {
			spec = spec.and((policy, query, cb) -> cb.equal(policy.get("policyKey"), policyKey));
		}
		if (audience != null) {
			spec = spec.and((policy, query, cb) -> cb.equal(policy.get("audience"), audience));
		}
		if (published != null) {
			spec = spec.and((policy, query, cb) -> cb.equal(policy.get("published"), published));
		}
		return findAll(spec, pageable);
	}
}
