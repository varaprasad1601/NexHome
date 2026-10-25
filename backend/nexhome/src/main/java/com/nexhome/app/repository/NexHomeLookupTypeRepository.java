package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeLookupType;

@Repository
public interface NexHomeLookupTypeRepository extends JpaRepository<NexHomeLookupType, Long> {

	Optional<NexHomeLookupType> findByTypeCode(String typeCode);

	List<NexHomeLookupType> findAllByOrderByTypeCodeAsc();
}
