package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeRoleFunction;

@Repository
public interface NexHomeRoleFunctionRepository extends JpaRepository<NexHomeRoleFunction, Long> {

	Optional<NexHomeRoleFunction> findByRoleRoleIdAndFunctionFunctionId(Long roleId, Long functionId);
}
