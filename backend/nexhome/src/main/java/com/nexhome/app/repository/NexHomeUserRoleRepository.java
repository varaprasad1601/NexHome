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

	/** Whether the user currently holds any of the roles. */
	boolean existsByUserIdAndRoleCodeInAndActiveTrueAndRoleActiveFlagTrue(Long userId, Collection<String> roleCodes);

	/**
	 * How many current holders of the role (active assignment of an active role) were given it in each month (IST),
	 * oldest first; months without any are left out.
	 */
	@Query(nativeQuery = true, value = """
			select to_char(ur.assigned_at at time zone 'Asia/Kolkata', 'YYYY-MM') as month, count(*) as total
			from nexhome_user_roles ur
			join nexhome_roles r on r.id = ur.role_id
			where r.code = :roleCode and ur.is_active and r.active_flag
			group by 1
			order by 1""")
	List<MonthlyCount> countActiveByRoleAndMonth(@Param("roleCode") String roleCode);

	/** One row of {@link #countActiveByRoleAndMonth}. */
	interface MonthlyCount {

		/** {@code YYYY-MM}. */
		String getMonth();

		Long getTotal();
	}
}
