package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Sets an account's profile photo from an upload ({@code POST /api/files/upload}, purpose PROFILE_PHOTO: JPEG, PNG or
 * WebP up to 2 MB); a null {@code fileId} removes the photo. The previous stored photo is deleted.
 */
public record NexHomeUserPhotoUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		Long fileId) {
}
