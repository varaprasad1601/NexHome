package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePushDevice;

@Repository
public interface NexHomePushDeviceRepository extends JpaRepository<NexHomePushDevice, Long> {

	/** The device with this FCM token (unique). */
	Optional<NexHomePushDevice> findByDeviceToken(String deviceToken);

	/** The active devices of these users, with the user (for the push dispatcher). */
	@EntityGraph(attributePaths = "user")
	List<NexHomePushDevice> findByUserIdInAndActiveTrue(Collection<Long> userIds);
}
