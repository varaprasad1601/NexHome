import { apiRequest } from './client';

/**
 * Sends a LOGIN OTP for the admin sign-in (POST /api/auth/admin/otp/request). The backend answers 404
 * "Invalid user. ..." unless the number belongs to an active SUPER_USER, ADMIN or SUB_ADMIN account.
 */
export function requestAdminLoginOtp(countryCode, mobileNumber) {
  return apiRequest('/api/auth/admin/otp/request', {
    method: 'POST',
    body: { countryCode, mobileNumber }
  });
}

/** Verifies the LOGIN OTP; the response carries the user and its tokens (POST /api/auth/otp/verify). */
export function verifyLoginOtp(countryCode, mobileNumber, otp) {
  return apiRequest('/api/auth/otp/verify', {
    method: 'POST',
    body: { countryCode, mobileNumber, purpose: 'LOGIN', otp }
  });
}

/** Exchanges the refresh token for new tokens; the old one is used up (POST /api/auth/token/refresh). */
export function refreshTokens(refreshToken) {
  return apiRequest('/api/auth/token/refresh', { method: 'POST', body: { refreshToken } });
}

/** Ends the session of the refresh token (POST /api/auth/logout). */
export function logout(refreshToken) {
  return apiRequest('/api/auth/logout', { method: 'POST', body: { refreshToken } });
}
