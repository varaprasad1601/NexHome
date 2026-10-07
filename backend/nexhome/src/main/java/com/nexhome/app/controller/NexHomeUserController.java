package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeUserRegisterRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.service.NexHomeUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class NexHomeUserController {

	private final NexHomeUserService userService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeUserResponse register(@Valid @RequestBody NexHomeUserRegisterRequest request) {
		return userService.register(request);
	}
}
