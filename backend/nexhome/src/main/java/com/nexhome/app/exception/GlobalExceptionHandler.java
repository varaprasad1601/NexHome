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
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexhome.app.model.ExceptionBody;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Returns every error as {@code ResponseEntity<ExceptionBody>}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** Error timestamps are reported in India time regardless of the server's time zone. */
	private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Kolkata");

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<ExceptionBody> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<ExceptionBody> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
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
		HttpStatus resolved = HttpStatus.resolve(status.value());
		ExceptionBody body = new ExceptionBody(
				request.getRequestURI(),
				request.getMethod(),
				status.value(),
				resolved != null ? resolved.getReasonPhrase() : "Error",
				message,
				ZonedDateTime.now(TIME_ZONE));
		return ResponseEntity.status(status).body(body);
	}
}
