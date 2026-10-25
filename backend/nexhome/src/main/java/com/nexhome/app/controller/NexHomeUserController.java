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

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.service.NexHomeUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Users and their roles. Users are never deleted: set the account status to DEACTIVATED instead. Access is checked
 * against the USER_MANAGE function; users may always read and update their own account.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class NexHomeUserController {

	private final NexHomeUserService userService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('USER_MANAGE', 'WRITE')")
	public NexHomeUserResponse createUser(@Valid @RequestBody NexHomeUserCreateRequest request) {
		return userService.createUser(request);
	}

	/** The signed-in user. */
	@GetMapping("/me")
	public NexHomeUserResponse getCurrentUser() {
		return userService.getCurrentUser();
	}

	/**
	 * Users who currently hold the CUSTOMER role. Query parameters (all optional): {@code page} (0-based),
	 * {@code size} (1-100, default 10), {@code accountStatus}, {@code search} (part of the mobile number or email).
	 */
	@GetMapping("/customers")
	@PreAuthorize("@permissions.can('USER_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeUserResponse> getCustomers(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.CUSTOMER, page, size, accountStatus, search);
	}

	/** Users who currently hold the PARTNER role; same query parameters as /customers. */
	@GetMapping("/partners")
	@PreAuthorize("@permissions.can('USER_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeUserResponse> getPartners(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.PARTNER, page, size, accountStatus, search);
	}

	/** Users who currently hold the ADMIN role; same query parameters as /customers. Not for sub-admins. */
	@GetMapping("/admins")
	@PreAuthorize("@permissions.can('STAFF_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeUserResponse> getAdmins(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String accountStatus,
			@RequestParam(required = false) String search) {
		return userService.getUsersByRole(NexHomeRole.ADMIN, page, size, accountStatus, search);
	}

	/** Users who currently hold the SUB_ADMIN role; same query parameters as /customers. Not for sub-admins. */
	@GetMapping("/sub-admins")
	@PreAuthorize("@permissions.can('STAFF_MANAGE', 'READ')")
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
	@PreAuthorize("@permissions.isSelfOrCan(#userId, 'USER_MANAGE', 'READ')")
	public NexHomeUserResponse getUser(@PathVariable Long userId) {
		return userService.getUser(userId);
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'USER_MANAGE', 'WRITE')")
	public NexHomeUserResponse updateUser(@Valid @RequestBody NexHomeUserUpdateRequest request) {
		return userService.updateUser(request);
	}

	/** Admin sets the account status (ACTIVE, SUSPENDED, BLOCKED, DEACTIVATED). */
	@PutMapping("/status/update")
	@PreAuthorize("@permissions.can('USER_MANAGE', 'WRITE')")
	public NexHomeUserResponse updateUserStatus(@Valid @RequestBody NexHomeUserStatusUpdateRequest request) {
		return userService.updateUserStatus(request);
	}

	@PostMapping("/roles/assign")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('USER_MANAGE', 'WRITE')")
	public NexHomeUserResponse assignRole(@Valid @RequestBody NexHomeUserRoleAssignRequest request) {
		return userService.assignRole(request);
	}

	@PutMapping("/roles/update")
	@PreAuthorize("@permissions.can('USER_MANAGE', 'WRITE')")
	public NexHomeUserResponse updateUserRole(@Valid @RequestBody NexHomeUserRoleUpdateRequest request) {
		return userService.updateUserRole(request);
	}
}
