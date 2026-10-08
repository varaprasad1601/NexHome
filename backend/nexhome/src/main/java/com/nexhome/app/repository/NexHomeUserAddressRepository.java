package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUserAddress;

@Repository
public interface NexHomeUserAddressRepository extends JpaRepository<NexHomeUserAddress, Long> {

	// At most one match: the partial unique index allows one active address per user
	Optional<NexHomeUserAddress> findByUserUserIdAndActiveFlag(Long userId, boolean activeFlag);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeUserAddress a where a.user.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeUserAddress a where a.user.userId in "
			+ "(select u.userId from NexHomeUser u where u.role.roleId = :roleId)")
	int deleteAllByRoleId(@Param("roleId") Long roleId);
}
