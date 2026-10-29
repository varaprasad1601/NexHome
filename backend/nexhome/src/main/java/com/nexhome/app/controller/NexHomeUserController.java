package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeCustomerResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePartnerResponse;
import com.nexhome.app.dto.NexHomePartnerStatsResponse;
import com.nexhome.app.dto.NexHomeStaffResponse;
import com.nexhome.app.dto.NexHomeStaffStatsResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserPermissionsResponse;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatsResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.service.NexHomePermissionService;
import com.nexhome.app.service.NexHomeUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Users and their roles. Users are never deleted: set the account status to DEACTIVATED instead. Access is checked
 * against the function of the account's role (ADMIN_MANAGE, SUB_ADMIN_MANAGE, CUSTOMER_MANAGE, PARTNER_MANAGE; super
 * users only for SUPER_USER and other roles); users may always read and update their own account.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class NexHomeUserController {

	private final NexHomeUserService userService;
	private final NexHomePermissionService permissionService;

	/**
	 * Needs write on the role's function (ADMIN_MANAGE, SUB_ADMIN_MANAGE, CUSTOMER_MANAGE, PARTNER_MANAGE; SUPER_USER
	 * and other roles: super users); the service checks.
	 */
	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("isAuthenticated()")
	public NexHomeUserResponse createUser(@Valid @RequestBody NexHomeUserCreateRequest request) {
		return userService.createUser(request);
	}

	/** The signed-in user. */
	@GetMapping("/me")
	public NexHomeUserResponse getCurrentUser() {
		return userService.getCurrentUser();
	}

	/** What the signed-in user may do, function by function (the admin console hides the rest). */
	@GetMapping("/me/permissions")
	public NexHomeUserPermissionsResponse getCurrentUserPermissions() {
		return permissionService.currentUserPermissions();
	}

	/**
	 * Users who currently hold the CUSTOMER role, with their customer profile's id, public id and name. Query
	 * parameters (all optional): {@code page} (0-based), {@code size} (1-100, default 10), {@code accountStatus},
	 * {@code search} (part of the mobile number, email, "first last" name or public id).
	 */
	@GetMapping("/customers")
	@PreAuthorize("@permissions.can('CUSTOMER_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeCustomerResponse> getCustomers(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getCustomers(page, size, accountStatus, search);
	}

	/** Customer counts by account status (the console's Manage Customers tiles). */
	@GetMapping("/customers/stats")
	@PreAuthorize("@permissions.can('CUSTOMER_MANAGE', 'READ')")
	public NexHomeStaffStatsResponse getCustomerStats() {
		return userService.getCustomerStats();
	}

	/**
	 * Users who currently hold the PARTNER role, with their onboarding profile's id, public id, name, city, main
	 * service category and statuses (null until the profile is started). Query parameters as /customers, plus
	 * {@code onboardingStatus} (a PARTNER_ONBOARDING_STATUS code, or NOT_STARTED for partners without a profile) and
	 * {@code mainServiceCategoryId} (partners whose main service category it is).
	 */
	@GetMapping("/partners")
	@PreAuthorize("@permissions.can('PARTNER_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePartnerResponse> getPartners(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String onboardingStatus,
			@RequestParam(required = false) Long mainServiceCategoryId, @RequestParam(required = false) String search) {
		return userService.getPartners(page, size, accountStatus, onboardingStatus, mainServiceCategoryId, search);
	}

	/** Partner counts: total, approved, awaiting review, not submitted, online (the console's Manage Partners tiles). */
	@GetMapping("/partners/stats")
	@PreAuthorize("@permissions.can('PARTNER_MANAGE', 'READ')")
	public NexHomePartnerStatsResponse getPartnerStats() {
		return userService.getPartnerStats();
	}

	/**
	 * Customer and partner totals, this month's additions and the last 10 months (admin dashboard): customers with
	 * CUSTOMER_MANAGE read, partners with PARTNER_MANAGE read (the other is null); 403 with neither.
	 */
	@GetMapping("/stats")
	@PreAuthorize("isAuthenticated()")
	public NexHomeUserStatsResponse getUserStats() {
		return userService.getUserStats();
	}

	/**
	 * Staff accounts (SUPER_USER, ADMIN, SUB_ADMIN holders) with their profile names, for the admin console. Query
	 * parameters (all optional): {@code page}, {@code size}, {@code roleCode}, {@code accountStatus}, {@code search}
	 * (mobile number, email, name or profile public id). Only the staff roles the caller may read are listed
	 * (ADMIN_MANAGE, SUB_ADMIN_MANAGE read; super users for super users); asking for another role is 403.
	 */
	@GetMapping("/staff")
	@PreAuthorize("isAuthenticated()")
	public NexHomePageResponse<NexHomeStaffResponse> getStaff(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String roleCode,
			@RequestParam(required = false) String accountStatus, @RequestParam(required = false) String search) {
		return userService.getStaff(page, size, roleCode, accountStatus, search);
	}

	/** Staff account counts by status, with the same visibility as /staff; optional {@code roleCode}. */
	@GetMapping("/staff/stats")
	@PreAuthorize("isAuthenticated()")
	public NexHomeStaffStatsResponse getStaffStats(@RequestParam(required = false) String roleCode) {
		return userService.getStaffStats(roleCode);
	}

	/** Users who currently hold the ADMIN role; same query parameters as /customers. */
	@GetMapping("/admins")
	@PreAuthorize("@permissions.can('ADMIN_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeUserResponse> getAdmins(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.ADMIN, page, size, accountStatus, search);
	}

	/** Users who currently hold the SUB_ADMIN role; same query parameters as /customers. */
	@GetMapping("/sub-admins")
	@PreAuthorize("@permissions.can('SUB_ADMIN_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeUserResponse> getSubAdmins(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.SUB_ADMIN, page, size, accountStatus, search);
	}

	/**
	 * Users who currently hold the SUPER_USER role; same query parameters as /customers. Only super users may call
	 * it; this is not grantable through role functions.
	 */
	@GetMapping("/super-users")
	@PreAuthorize("@permissions.isCurrentUserSuperUser()")
	public NexHomePageResponse<NexHomeUserResponse> getSuperUsers(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.SUPER_USER, page, size, accountStatus, search);
	}

	@GetMapping("/{userId}")
	@PreAuthorize("@permissions.isSelfOrCanManageAccount(#userId, 'READ')")
	public NexHomeUserResponse getUser(@PathVariable Long userId) {
		return userService.getUser(userId);
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.isSelfOrCanManageAccount(#request.userId(), 'WRITE')")
	public NexHomeUserResponse updateUser(@Valid @RequestBody NexHomeUserUpdateRequest request) {
		return userService.updateUser(request);
	}

	/**
	 * Sets the account status (ACTIVE, SUSPENDED, BLOCKED, DEACTIVATED). Needs write on the account's role function
	 * (a staff account's highest role; see NexHomePermissionService.canManageAccount); the service checks.
	 */
	@PutMapping("/status/update")
	@PreAuthorize("isAuthenticated()")
	public NexHomeUserResponse updateUserStatus(@Valid @RequestBody NexHomeUserStatusUpdateRequest request) {
		return userService.updateUserStatus(request);
	}

	/** Giving or withdrawing a role needs write on that role's function; the service checks. */
	@PostMapping("/roles/assign")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("isAuthenticated()")
	public NexHomeUserResponse assignRole(@Valid @RequestBody NexHomeUserRoleAssignRequest request) {
		return userService.assignRole(request);
	}

	@PutMapping("/roles/update")
	@PreAuthorize("isAuthenticated()")
	public NexHomeUserResponse updateUserRole(@Valid @RequestBody NexHomeUserRoleUpdateRequest request) {
		return userService.updateUserRole(request);
	}
}
