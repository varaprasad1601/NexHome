package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeServiceMedia;

@Repository
public interface NexHomeServiceMediaRepository extends JpaRepository<NexHomeServiceMedia, Long> {

	List<NexHomeServiceMedia> findByServiceIdOrderByDisplayOrderAscIdAsc(Long serviceId);

	boolean existsByServiceId(Long serviceId);
}
