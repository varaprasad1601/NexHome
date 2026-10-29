package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAuditLog;

@Repository
public interface NexHomeAuditLogRepository extends JpaRepository<NexHomeAuditLog, Long>,
		JpaSpecificationExecutor<NexHomeAuditLog> {

	/** The entity types and actions logged so far, for the console's filters: [entity type, action]. */
	@Query("select distinct a.entityType, a.action from NexHomeAuditLog a order by 1, 2")
	List<Object[]> findEntityTypesAndActions();

	/**
	 * The audit list: only the filters given (no null checks in JPQL). {@code search} (trimmed, lower case) matches
	 * the entity id and the action.
	 */
	static Specification<NexHomeAuditLog> auditSpec(String entityType, String entityId, String action,
			Long actorUserId, Instant from, Instant to, String search) {
		Specification<NexHomeAuditLog> spec = (log, query, cb) -> cb.conjunction();
		if (entityType != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("entityType"), entityType));
		}
		if (entityId != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("entityId"), entityId));
		}
		if (action != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("action"), action));
		}
		if (actorUserId != null) {
			spec = spec.and((log, query, cb) -> cb.equal(log.get("actor").get("id"), actorUserId));
		}
		if (from != null) {
			spec = spec.and((log, query, cb) -> cb.greaterThanOrEqualTo(log.get("creationDate"), from));
		}
		if (to != null) {
			spec = spec.and((log, query, cb) -> cb.lessThan(log.get("creationDate"), to));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((log, query, cb) -> cb.or(
					cb.like(cb.lower(log.get("entityId")), pattern, '\\'),
					cb.like(cb.lower(log.get("action")), pattern, '\\')));
		}
		return spec;
	}
}
