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

	/**
	 * The active staff (SUPER_USER, ADMIN or SUB_ADMIN held through an active role) who may read {@code functionCode}:
	 * super users always, others through an active grant of an active function. Not the system account (user 1).
	 * Staff notifications go to them.
	 */
	@Query(nativeQuery = true, value = """
			select distinct u.id
			from nexhome_users u
			join nexhome_user_roles ur on ur.user_id = u.id and ur.is_active
			join nexhome_roles r on r.id = ur.role_id and r.active_flag
			where u.account_status = 'ACTIVE' and u.id <> 1 and r.code in ('SUPER_USER', 'ADMIN', 'SUB_ADMIN')
				and (r.code = 'SUPER_USER' or exists (
					select 1
					from nexhome_role_functions rf
					join nexhome_functions f on f.id = rf.function_id
					where rf.role_id = r.id and rf.active_flag and rf.can_read and f.active_flag
						and f.function_code = :functionCode))""")
	List<Long> findStaffWhoCanRead(@Param("functionCode") String functionCode);

	/** Active accounts currently holding any of the roles (active assignment of an active role); not user 1. */
	@Query(nativeQuery = true, value = """
			select distinct u.id
			from nexhome_users u
			join nexhome_user_roles ur on ur.user_id = u.id and ur.is_active
			join nexhome_roles r on r.id = ur.role_id and r.active_flag
			where u.account_status = 'ACTIVE' and u.id <> 1 and r.code in (:roleCodes)""")
	List<Long> findActiveHoldersOf(@Param("roleCodes") Collection<String> roleCodes);

	/** One row of {@link #countActiveByRoleAndMonth}. */
	interface MonthlyCount {

		/** {@code YYYY-MM}. */
		String getMonth();

		Long getTotal();
	}
}
