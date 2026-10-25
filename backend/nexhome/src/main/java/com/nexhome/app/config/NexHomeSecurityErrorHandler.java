package com.nexhome.app.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.nexhome.app.exception.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the usual {@code ExceptionBody} JSON for requests the security filters reject before they reach a
 * controller (GlobalExceptionHandler does not see those).
 */
@Component
@RequiredArgsConstructor
public class NexHomeSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	/** No token, or an invalid, expired or wrongly signed one: 401. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		String message;
		if (!(authException instanceof InvalidBearerTokenException)) {
			message = GlobalExceptionHandler.AUTHENTICATION_REQUIRED_MESSAGE;
		}
		else if (authException.getMessage() != null
				&& authException.getMessage().contains(NexHomeSessionTokenValidator.SESSION_ENDED)) {
			message = NexHomeSessionTokenValidator.SESSION_ENDED;
		}
		else {
			message = "The access token is invalid or has expired.";
		}
		write(response, HttpStatus.UNAUTHORIZED, message, request);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpStatus.FORBIDDEN, GlobalExceptionHandler.ACCESS_DENIED_MESSAGE, request);
	}

	private void write(HttpServletResponse response, HttpStatus status, String message, HttpServletRequest request)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(),
				GlobalExceptionHandler.toExceptionBody(status, message, request));
	}
}
