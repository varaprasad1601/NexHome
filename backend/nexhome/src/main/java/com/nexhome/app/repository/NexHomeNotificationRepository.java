package com.nexhome.app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeNotification;

@Repository
public interface NexHomeNotificationRepository extends JpaRepository<NexHomeNotification, Long>,
		JpaSpecificationExecutor<NexHomeNotification> {

	/** A page of notifications with their users (the console's sent list shows who got each). */
	@Override
	@EntityGraph(attributePaths = "user")
	Page<NexHomeNotification> findAll(Specification<NexHomeNotification> spec, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);

	/** Marks the given notifications of the user read; returns how many changed. */
	@Modifying
	@Query("update NexHomeNotification n set n.readAt = :now, n.updatedBy = :userId, n.updatedDate = :now "
			+ "where n.user.id = :userId and n.readAt is null and n.id in :ids")
	int markRead(@Param("userId") Long userId, @Param("ids") Collection<Long> ids, @Param("now") Instant now);

	/** Marks every unread notification of the user read; returns how many changed. */
	@Modifying
	@Query("update NexHomeNotification n set n.readAt = :now, n.updatedBy = :userId, n.updatedDate = :now "
			+ "where n.user.id = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

	/** Deletes every notification of the user (their delivery logs keep a null notification); returns how many. */
	@Modifying
	@Query("delete from NexHomeNotification n where n.user.id = :userId")
	int deleteAllOfUser(@Param("userId") Long userId);

	/** The user's unread notifications by type: [type, count]. */
	@Query("select n.notificationType, count(n) from NexHomeNotification n where n.user.id = :userId "
			+ "and n.readAt is null group by n.notificationType")
	List<Object[]> countUnreadByType(@Param("userId") Long userId);

	long countByUserId(Long userId);

	/** Notifications by type: [type, count] (the console's sent list). */
	@Query("select n.notificationType, count(n) from NexHomeNotification n group by n.notificationType")
	List<Object[]> countByType();

	@Query("select count(n) from NexHomeNotification n where n.readAt is null")
	long countUnread();

	/**
	 * Notifications of one user, or (user null) of everyone: only the filters given. {@code unread}: true only
	 * unread, false only read. {@code search} (trimmed, lower case) matches the title and message.
	 */
	static Specification<NexHomeNotification> notificationSpec(Long userId, String type, Boolean unread,
			String search) {
		Specification<NexHomeNotification> spec = (notification, query, cb) -> cb.conjunction();
		if (userId != null) {
			spec = spec.and((notification, query, cb) -> cb.equal(notification.get("user").get("id"), userId));
		}
		if (type != null) {
			spec = spec.and((notification, query, cb) -> cb.equal(notification.get("notificationType"), type));
		}
		if (Boolean.TRUE.equals(unread)) {
			spec = spec.and((notification, query, cb) -> cb.isNull(notification.get("readAt")));
		}
		else if (Boolean.FALSE.equals(unread)) {
			spec = spec.and((notification, query, cb) -> cb.isNotNull(notification.get("readAt")));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((notification, query, cb) -> cb.or(
					cb.like(cb.lower(notification.get("title")), pattern, '\\'),
					cb.like(cb.lower(notification.get("message")), pattern, '\\')));
		}
		return spec;
	}
}
