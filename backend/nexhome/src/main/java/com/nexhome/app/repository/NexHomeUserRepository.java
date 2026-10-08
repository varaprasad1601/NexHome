package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUser;

@Repository
public interface NexHomeUserRepository extends JpaRepository<NexHomeUser, Long> {

    Optional<NexHomeUser> findByUserId(Long userId);

    Optional<NexHomeUser> findByPhone(String phone);

	Optional<NexHomeUser> findByPhoneAndRoleRoleId(String phone, Long roleId);

	Optional<NexHomeUser> findByEmail(String email);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeUser u where u.role.roleId = :roleId")
	int deleteAllByRoleId(@Param("roleId") Long roleId);

}
