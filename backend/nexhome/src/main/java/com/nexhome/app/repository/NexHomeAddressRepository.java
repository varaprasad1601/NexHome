package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAddress;

@Repository
public interface NexHomeAddressRepository extends JpaRepository<NexHomeAddress, Long> {

	/** The default address first, then newest first. */
	List<NexHomeAddress> findByUserIdOrderByDefaultAddressDescCreationDateDescIdDesc(Long userId);

	/** The customer's default address; unique by the partial index ux_nexhome_addresses_one_default. */
	Optional<NexHomeAddress> findByUserIdAndDefaultAddressTrue(Long userId);
}
