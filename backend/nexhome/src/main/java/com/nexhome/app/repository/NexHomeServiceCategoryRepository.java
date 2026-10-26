package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeServiceCategory;

@Repository
public interface NexHomeServiceCategoryRepository extends JpaRepository<NexHomeServiceCategory, Long> {

	/** Slugs are unique. */
	Optional<NexHomeServiceCategory> findBySlug(String slug);

	Optional<NexHomeServiceCategory> findByIdAndActiveTrue(Long id);

	List<NexHomeServiceCategory> findAllByOrderByDisplayOrderAscNameAsc();

	List<NexHomeServiceCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();
}
