package com.nexhome.app.dto;

import java.util.List;

/**
 * The signed-in user's permissions, for clients that show only what the user may use (the admin console hides menus
 * without read and actions without write or delete). The backend still checks every request itself.
 *
 * @param isSuperUser an ACTIVE SUPER_USER, who may do everything; {@code functions} then lists every active function
 *        with full access
 * @param functions the active functions the user has any access to, by module and code; empty for an account that is
 *        not ACTIVE
 */
public record NexHomeUserPermissionsResponse(
		boolean isSuperUser,
		List<NexHomeFunctionAccessResponse> functions) {
}
