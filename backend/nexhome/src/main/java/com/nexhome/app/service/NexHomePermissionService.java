package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomeUserPermissionsResponse;

/**
 * Who is calling and what they may do. Controllers use it in {@code @PreAuthorize} through the bean name
 * {@code permissions}, e.g. {@code @PreAuthorize("@permissions.can('CUSTOMER_MANAGE', 'WRITE')")}; services use it for
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

	/**
	 * Everything the caller may do, function by function (the union of their active roles' grants), so that clients
	 * can hide what the user cannot use. A super user gets every active function with full access.
	 */
	NexHomeUserPermissionsResponse currentUserPermissions();

	/**
	 * Whether the caller may use accounts of the role, through the role's function: ADMIN_MANAGE, SUB_ADMIN_MANAGE,
	 * CUSTOMER_MANAGE, PARTNER_MANAGE. SUPER_USER, any other role and no role (null): super users only.
	 *
	 * @param access READ, WRITE or DELETE
	 */
	boolean canManageRole(String roleCode, String access);

	/**
	 * {@link #canManageRole} for the user's highest staff role (SUPER_USER, then ADMIN, then SUB_ADMIN); false when
	 * the user holds none.
	 */
	boolean canManageStaff(Long userId, String access);

	/**
	 * Whether the caller may use this account: a staff account by its highest staff role ({@link #canManageStaff}),
	 * any other by any of its active roles (a customer and partner through either function); an account without an
	 * active role by super users only.
	 */
	boolean canManageAccount(Long userId, String access);

	/** Whether the caller is the given user (and ACTIVE), or {@link #canManageAccount}. */
	boolean isSelfOrCanManageAccount(Long userId, String access);

	/** The user's highest active staff role code (SUPER_USER, ADMIN or SUB_ADMIN), or null. */
	String highestStaffRole(Long userId);

	/** The staff roles whose accounts the caller may read, highest first. */
	List<String> readableStaffRoles();
}
