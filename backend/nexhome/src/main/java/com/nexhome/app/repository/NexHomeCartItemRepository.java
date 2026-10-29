package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeCartItem;

@Repository
public interface NexHomeCartItemRepository extends JpaRepository<NexHomeCartItem, Long> {

	/** Oldest first, with the service and variant in the same query. */
	@EntityGraph(attributePaths = { "service", "variant" })
	List<NexHomeCartItem> findByCartIdOrderByIdAsc(Long cartId);
}
