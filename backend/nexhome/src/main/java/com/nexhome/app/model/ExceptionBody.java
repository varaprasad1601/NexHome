package com.nexhome.app.model;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of every API error response. Not a JPA entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionBody {

	/** Request path, e.g. {@code /api/users/create}. */
	private String path;

	/** HTTP method, e.g. {@code POST}. */
	private String method;

	/** HTTP status code, e.g. {@code 409}. */
	private int statusCode;

	/** HTTP reason phrase, e.g. {@code Conflict}. */
	private String error;

	/** Client-safe description of what went wrong. */
	private String message;

	/** When the error occurred, e.g. {@code Thu, 08 Oct 2026 01:42:30 IST}. */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
			timezone = "Asia/Kolkata")
	private ZonedDateTime timestamp;
}
