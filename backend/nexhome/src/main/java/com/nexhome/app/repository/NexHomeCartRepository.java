package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeCart;

@Repository
public interface NexHomeCartRepository extends JpaRepository<NexHomeCart, Long> {

	/** The customer's ACTIVE cart; unique by the partial index ux_nexhome_carts_one_active. */
	Optional<NexHomeCart> findByCustomerIdAndStatus(Long customerId, String status);
}
