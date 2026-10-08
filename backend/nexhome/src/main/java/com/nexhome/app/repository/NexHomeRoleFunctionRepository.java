package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeRoleFunction;

@Repository
public interface NexHomeRoleFunctionRepository extends JpaRepository<NexHomeRoleFunction, Long> {

	Optional<NexHomeRoleFunction> findByRoleRoleIdAndFunctionFunctionId(Long roleId, Long functionId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeRoleFunction rf where rf.role.roleId = :roleId")
	int deleteAllByRoleId(@Param("roleId") Long roleId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NexHomeRoleFunction rf where rf.function.functionId = :functionId")
	int deleteAllByFunctionId(@Param("functionId") Long functionId);
}
