package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeRoleFunction;

@Repository
public interface NexHomeRoleFunctionRepository extends JpaRepository<NexHomeRoleFunction, Long> {

	Optional<NexHomeRoleFunction> findByRoleIdAndFunctionId(Short roleId, Long functionId);

	boolean existsByRoleId(Short roleId);

	boolean existsByFunctionId(Long functionId);

	/**
	 * Whether any of the user's active roles grants the function with the requested flags. The role, the role
	 * function and the function must all be active.
	 */
	@Query("""
			select count(rf) > 0
			from NexHomeRoleFunction rf, NexHomeUserRole ur
			where ur.user.id = :userId and ur.active = true and ur.role = rf.role and rf.role.activeFlag = true
				and rf.activeFlag = true and rf.function.activeFlag = true and rf.function.functionCode = :functionCode
				and (:read = false or rf.canRead = true)
				and (:write = false or rf.canWrite = true)
				and (:delete = false or rf.canDelete = true)""")
	boolean hasAccess(@Param("userId") Long userId, @Param("functionCode") String functionCode,
			@Param("read") boolean read, @Param("write") boolean write, @Param("delete") boolean delete);
}
