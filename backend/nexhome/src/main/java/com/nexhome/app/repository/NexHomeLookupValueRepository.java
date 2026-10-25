package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeLookupValue;

@Repository
public interface NexHomeLookupValueRepository extends JpaRepository<NexHomeLookupValue, Long> {

	Optional<NexHomeLookupValue> findByLookupTypeIdAndValueCode(Long lookupTypeId, String valueCode);

	/** The type's default value; unique by the partial index ux_nexhome_lookup_values_one_default. */
	Optional<NexHomeLookupValue> findByLookupTypeIdAndDefaultValueTrue(Long lookupTypeId);

	/** Used to validate a submitted code: the value and its type must both be active. */
	boolean existsByLookupTypeTypeCodeAndLookupTypeActiveFlagTrueAndValueCodeAndActiveFlagTrue(String typeCode,
			String valueCode);

	List<NexHomeLookupValue> findByLookupTypeTypeCodeAndActiveFlagTrueOrderByDisplayOrderAscDisplayNameAsc(
			String typeCode);

	List<NexHomeLookupValue> findByLookupTypeIdOrderByDisplayOrderAscDisplayNameAsc(Long lookupTypeId);
}
