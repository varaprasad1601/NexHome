package com.nexhome.app.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAuthRefreshToken;

@Repository
public interface NexHomeAuthRefreshTokenRepository extends JpaRepository<NexHomeAuthRefreshToken, Long> {

	Optional<NexHomeAuthRefreshToken> findByTokenHash(String tokenHash);

	/** Whether the login session is still open: it has an unrevoked, unexpired refresh token. */
	boolean existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfter(UUID sessionId, Instant now);

	/** Revokes every session of the user that is not revoked yet; returns how many. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update NexHomeAuthRefreshToken t
			set t.revokedAt = :now, t.revokedReason = :reason, t.updatedDate = :now, t.updatedBy = :actingUserId
			where t.user.id = :userId and t.revokedAt is null""")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Instant now,
			@Param("actingUserId") Long actingUserId);
}
