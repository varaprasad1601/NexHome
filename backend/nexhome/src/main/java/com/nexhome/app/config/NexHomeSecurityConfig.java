package com.nexhome.app.config;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Stateless JWT authentication. Access tokens are HS256 JWTs issued by NexHomeAuthService after an OTP login; every
 * other request must carry one as {@code Authorization: Bearer <token>}. What an authenticated user may do is decided
 * per endpoint with {@code @PreAuthorize} and the {@code permissions} bean (role functions).
 */
@Configuration
@EnableMethodSecurity
public class NexHomeSecurityConfig {

	/** Reachable without an access token. */
	private static final String[] PUBLIC_POST_PATHS = {
			"/api/auth/register",
			"/api/auth/otp/request",
			"/api/auth/admin/otp/request",
			"/api/auth/otp/verify",
			"/api/auth/token/refresh",
			"/api/auth/logout" };

	/** Public reads. The services decide what anonymous callers see (e.g. only active, published entries). */
	private static final String[] PUBLIC_GET_PATHS = {
			"/api/catalog/categories",
			"/api/catalog/services",
			"/api/catalog/services/*",
			"/api/catalog/services/slug/*",
			"/api/faqs",
			"/api/policies/current",
			"/api/policies/current/*" };

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, NexHomeSecurityErrorHandler errorHandler)
			throws Exception {
		http
				// Bearer tokens are not sent automatically by browsers, so CSRF protection is not needed
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> {
				})
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
						// Dropdown options, also needed by the sign-up forms
						.requestMatchers(HttpMethod.GET, "/api/lookups/values/*").permitAll()
						// Browsing the catalogue, published FAQs and current policies; staff see more with a token
						.requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler));
		return http.build();
	}

	@Bean
	public SecretKey jwtSigningKey(NexHomeSecurityProperties properties) {
		byte[] key;
		try {
			key = Base64.getDecoder().decode(properties.jwtSecret() == null ? "" : properties.jwtSecret().trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("nexhome.security.jwt-secret (JWT_SECRET) must be Base64-encoded", ex);
		}
		if (key.length < 32) {
			throw new IllegalStateException(
					"nexhome.security.jwt-secret (JWT_SECRET) must be a Base64-encoded key of at least 32 bytes");
		}
		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	/**
	 * Checks the signature, expiry and issuer of incoming access tokens, then (only for tokens that pass) that their
	 * login session is still open, so logged-out tokens stop working at once.
	 */
	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, NexHomeSecurityProperties properties,
			NexHomeSessionTokenValidator sessionValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.jwtIssuer());
		decoder.setJwtValidator(jwt -> {
			OAuth2TokenValidatorResult result = standard.validate(jwt);
			return result.hasErrors() ? result : sessionValidator.validate(jwt);
		});
		return decoder;
	}

	/** The {@code roles} claim becomes ROLE_* authorities; the subject (the user id) becomes the principal name. */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(NexHomeSecurityProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		// Patterns, so dev can allow every local port (http://localhost:[*]); exact origins work as well
		cors.setAllowedOriginPatterns(
				properties.corsAllowedOrigins() == null ? List.of() : properties.corsAllowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}
