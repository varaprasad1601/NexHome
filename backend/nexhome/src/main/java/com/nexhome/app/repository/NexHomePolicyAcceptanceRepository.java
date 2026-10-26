package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePolicyAcceptance;

@Repository
public interface NexHomePolicyAcceptanceRepository extends JpaRepository<NexHomePolicyAcceptance, Long> {

	/** (user_id, policy_id) is unique: one acceptance per user and version. */
	boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

	/** The user's acceptances among the given versions. */
	List<NexHomePolicyAcceptance> findByUserIdAndPolicyIdIn(Long userId, Collection<Long> policyIds);

	/** Newest first, with the policy versions loaded in the same query. */
	@EntityGraph(attributePaths = "policy")
	List<NexHomePolicyAcceptance> findByUserIdOrderByAcceptedAtDescIdDesc(Long userId);

	boolean existsByPolicyId(Long policyId);
}
