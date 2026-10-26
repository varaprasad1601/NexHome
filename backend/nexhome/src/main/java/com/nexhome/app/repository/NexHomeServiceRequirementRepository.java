package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeServiceRequirement;

@Repository
public interface NexHomeServiceRequirementRepository extends JpaRepository<NexHomeServiceRequirement, Long> {

	List<NexHomeServiceRequirement> findByServiceIdOrderByDisplayOrderAscIdAsc(Long serviceId);

	boolean existsByServiceId(Long serviceId);
}
