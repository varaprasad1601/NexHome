package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeServiceImage;

@Repository
public interface NexHomeServiceImageRepository extends JpaRepository<NexHomeServiceImage, Long> {

	/** An image without its bytes, for lists. */
	interface Info {

		Long getId();

		String getFileName();

		String getContentType();

		int getFileSize();

		int getDisplayOrder();

		Instant getCreationDate();

		Long getCreatedBy();
	}

	List<Info> findByServiceIdOrderByDisplayOrderAscIdAsc(Long serviceId);

	long countByServiceId(Long serviceId);

	boolean existsByServiceId(Long serviceId);

	/** Moves one image, without loading its bytes. */
	@Modifying
	@Query("update NexHomeServiceImage i set i.displayOrder = :displayOrder, i.updatedBy = :updatedBy, "
			+ "i.updatedDate = CURRENT_TIMESTAMP where i.id = :id")
	void updateDisplayOrder(@Param("id") Long id, @Param("displayOrder") int displayOrder,
			@Param("updatedBy") Long updatedBy);
}
