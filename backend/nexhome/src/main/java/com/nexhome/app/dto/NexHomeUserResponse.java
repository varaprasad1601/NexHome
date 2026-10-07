package com.nexhome.app.dto;

import java.time.Instant;

public record NexHomeUserResponse(
		Long userId,
		String phone,
		String email,
		String roleName,
		Instant creationDate) {
}
