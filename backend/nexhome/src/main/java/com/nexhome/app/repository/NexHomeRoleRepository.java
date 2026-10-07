package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeRole;

@Repository
public interface NexHomeRoleRepository extends JpaRepository<NexHomeRole, Long> {

	Optional<NexHomeRole> findByRoleId(Long roleId);

	Optional<NexHomeRole> findByRoleName(String roleName);

}
