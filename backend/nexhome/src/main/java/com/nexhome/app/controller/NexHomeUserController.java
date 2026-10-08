package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressResponse;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.dto.NexHomeUserDetailsRequest;
import com.nexhome.app.dto.NexHomeUserDetailsResponse;
import com.nexhome.app.dto.NexHomeUserDetailsUpdateRequest;
import com.nexhome.app.dto.NexHomeUserPasswordSetRequest;
import com.nexhome.app.dto.NexHomeUserPasswordUpdateRequest;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.service.NexHomeUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class NexHomeUserController {

	private final NexHomeUserService userService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeUserResponse createUser(@Valid @RequestBody NexHomeUserCreateRequest request) {
		return userService.createUser(request);
	}

	@PostMapping("/details/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeUserDetailsResponse addUserDetails(@Valid @RequestBody NexHomeUserDetailsRequest request) {
		return userService.addUserDetails(request);
	}

	@PostMapping("/address/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeUserAddressResponse addUserAddress(@Valid @RequestBody NexHomeUserAddressRequest request) {
		return userService.addUserAddress(request);
	}

	@PutMapping("/update")
	public NexHomeUserResponse updateUser(@Valid @RequestBody NexHomeUserUpdateRequest request) {
		return userService.updateUser(request);
	}

	@DeleteMapping("/delete/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUser(@PathVariable Long userId) {
		userService.deleteUser(userId);
	}

	/** Admin approves or rejects a submitted user. */
	@PutMapping("/status/update")
	public NexHomeUserResponse updateUserStatus(@Valid @RequestBody NexHomeUserStatusUpdateRequest request) {
		return userService.updateUserStatus(request);
	}

	/** An approved user sets their first password, which activates the account. */
	@PostMapping("/password/set")
	public NexHomeUserResponse setPassword(@Valid @RequestBody NexHomeUserPasswordSetRequest request) {
		return userService.setPassword(request);
	}

	@PutMapping("/password/update")
	public NexHomeUserResponse updatePassword(@Valid @RequestBody NexHomeUserPasswordUpdateRequest request) {
		return userService.updatePassword(request);
	}

	@PutMapping("/details/update")
	public NexHomeUserDetailsResponse updateUserDetails(@Valid @RequestBody NexHomeUserDetailsUpdateRequest request) {
		return userService.updateUserDetails(request);
	}

	@PutMapping("/address/update")
	public NexHomeUserAddressResponse updateUserAddress(@Valid @RequestBody NexHomeUserAddressUpdateRequest request) {
		return userService.updateUserAddress(request);
	}

	@DeleteMapping("/address/delete/{userAddressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUserAddress(@PathVariable Long userAddressId) {
		userService.deleteUserAddress(userAddressId);
	}
}
