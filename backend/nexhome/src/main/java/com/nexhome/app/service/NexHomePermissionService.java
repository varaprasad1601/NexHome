package com.nexhome.app.service;

/**
 * Who is calling and what they may do. Controllers use it in {@code @PreAuthorize} through the bean name
 * {@code permissions}, e.g. {@code @PreAuthorize("@permissions.can('USER_MANAGE', 'WRITE')")}; services use it for
 * the acting user of audit columns.
 * <p>
 * Permissions are read from the database on every check, so revoking a role or role function takes effect
 * immediately, without waiting for access tokens to expire.
 */
public interface NexHomePermissionService {

	/**
	 * The authenticated user's id.
	 *
	 * @throws com.nexhome.app.exception.UnauthorizedException if the request is not authenticated
	 */
	Long currentUserId();

	/**
	 * Whether the authenticated, ACTIVE user may use the function: SUPER_USER always may, anyone else needs an active
	 * role function granting the access.
	 *
	 * @param access READ, WRITE or DELETE
	 */
	boolean can(String functionCode, String access);

	/**
	 * Like {@link #can}, but false instead of 401 when nobody is signed in: for public endpoints that show more to
	 * staff, e.g. inactive catalogue entries.
	 */
	boolean canIfSignedIn(String functionCode, String access);

	/** Whether the caller is the given user (and ACTIVE), or {@link #can} use the function. */
	boolean isSelfOrCan(Long userId, String functionCode, String access);

	/** Whether the user currently holds the SUPER_USER role. */
	boolean isSuperUser(Long userId);

	/**
	 * Whether the caller is an ACTIVE super user. For endpoints that only super users may ever use, independent of
	 * role functions (which could be granted to other roles).
	 */
	boolean isCurrentUserSuperUser();
}
