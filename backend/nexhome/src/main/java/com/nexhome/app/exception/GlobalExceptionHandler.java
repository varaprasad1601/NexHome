package com.nexhome.app.exception;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.nexhome.app.model.ExceptionBody;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Returns every error as {@code ResponseEntity<ExceptionBody>}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** Error timestamps are created in India time; ExceptionBody formats them for the response. */
	private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Kolkata");

	public static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	public static final String ACCESS_DENIED_MESSAGE = "You do not have permission to perform this action.";

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ExceptionBody> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	/** Spring Security failures inside a controller call (the filters' own failures go to NexHomeSecurityErrorHandler). */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ExceptionBody> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED_MESSAGE, request);
	}

	/** {@code @PreAuthorize} rejected the call. */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ExceptionBody> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE, request);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<ExceptionBody> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<ExceptionBody> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ExceptionBody> handleResourceNotFound(ResourceNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(TooManyRequestsException.class)
	public ResponseEntity<ExceptionBody> handleTooManyRequests(TooManyRequestsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ExceptionBody> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return build(HttpStatus.CONFLICT, "The request conflicts with existing data.", request);
	}

	/**
	 * Bean Validation failures. Constraint messages are written as full sentences in the DTOs
	 * (e.g. "Phone must be exactly 10 digits"), so only the messages are returned, one per invalid field.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ExceptionBody> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, String> firstErrorPerField = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			firstErrorPerField.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return build(HttpStatus.BAD_REQUEST, String.join(", ", firstErrorPerField.values()), request);
	}

	/** A path or query parameter of the wrong type, e.g. {@code ?page=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ExceptionBody> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Parameter " + ex.getName() + " has an invalid value.", request);
	}

	/** Missing or malformed JSON body. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ExceptionBody> handleUnreadableBody(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", request);
	}

	/**
	 * Spring MVC errors that carry their own status, e.g. unknown path (404), unsupported method (405)
	 * or content type (415); anything else is an unexpected 500.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ExceptionBody> handleOther(Exception ex, HttpServletRequest request) {
		if (ex instanceof ErrorResponse errorResponse) {
			String detail = errorResponse.getBody().getDetail();
			return build(errorResponse.getStatusCode(), detail != null ? detail : "Request could not be processed.",
					request);
		}
		// Log the details, but never expose internals to the client
		log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
	}

	private static ResponseEntity<ExceptionBody> build(HttpStatusCode status, String message,
			HttpServletRequest request) {
		return ResponseEntity.status(status).body(toExceptionBody(status, message, request));
	}

	/** The error body; also written by the security filters' error handler. */
	public static ExceptionBody toExceptionBody(HttpStatusCode status, String message, HttpServletRequest request) {
		HttpStatus resolved = HttpStatus.resolve(status.value());
		return new ExceptionBody(
				request.getRequestURI(),
				request.getMethod(),
				status.value(),
				resolved != null ? resolved.getReasonPhrase() : "Error",
				message,
				ZonedDateTime.now(TIME_ZONE));
	}
}
