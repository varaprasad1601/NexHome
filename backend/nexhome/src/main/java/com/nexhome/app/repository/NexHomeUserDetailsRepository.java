package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUserDetails;

@Repository
public interface NexHomeUserDetailsRepository extends JpaRepository<NexHomeUserDetails, Long> {

	// NexHomeUserDetails has a "user" field, not "userId", so the id is reached through it
	Optional<NexHomeUserDetails> findByUserUserId(Long userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeUserDetails d where d.user.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeUserDetails d where d.user.userId in "
			+ "(select u.userId from NexHomeUser u where u.role.roleId = :roleId)")
	int deleteAllByRoleId(@Param("roleId") Long roleId);
}
