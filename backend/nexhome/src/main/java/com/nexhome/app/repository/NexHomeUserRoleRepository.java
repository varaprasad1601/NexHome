package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.model.NexHomeUserRoleId;

@Repository
public interface NexHomeUserRoleRepository extends JpaRepository<NexHomeUserRole, NexHomeUserRoleId> {

	List<NexHomeUserRole> findByUserIdOrderByRoleIdAsc(Long userId);

	/** The role assignments of several users with their roles, in one query (for user lists). */
	@Query("select ur from NexHomeUserRole ur join fetch ur.role where ur.user.id in :userIds order by ur.role.id")
	List<NexHomeUserRole> findWithRoleByUserIdIn(@Param("userIds") Collection<Long> userIds);

	boolean existsByRoleId(Short roleId);

	/** Whether the user currently holds the role: the assignment and the role are both active. */
	boolean existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(Long userId, String roleCode);
}
