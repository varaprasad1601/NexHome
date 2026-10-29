package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeNotificationDeliveryLog;

@Repository
public interface NexHomeNotificationDeliveryLogRepository extends JpaRepository<NexHomeNotificationDeliveryLog, Long>,
		JpaSpecificationExecutor<NexHomeNotificationDeliveryLog> {

	/** A page of delivery logs with their notifications and users. */
	@Override
	@EntityGraph(attributePaths = { "notification", "user" })
	Page<NexHomeNotificationDeliveryLog> findAll(Specification<NexHomeNotificationDeliveryLog> spec,
			Pageable pageable);

	/** Delivery attempts by status: [status, count]. */
	@Query("select d.deliveryStatus, count(d) from NexHomeNotificationDeliveryLog d group by d.deliveryStatus")
	List<Object[]> countByStatus();

	/** Only the filters given (no null checks in JPQL). */
	static Specification<NexHomeNotificationDeliveryLog> deliverySpec(String channel, String status, Long userId) {
		Specification<NexHomeNotificationDeliveryLog> spec = (log, query, cb) -> cb.conjunction();
		if (channel != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("channel"), channel));
		}
		if (status != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("deliveryStatus"), status));
		}
		if (userId != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("user").get("id"), userId));
		}
		return spec;
	}
}
