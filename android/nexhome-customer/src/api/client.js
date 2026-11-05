import Constants from 'expo-constants';

/** The backend's port on the computer running it. */
const API_PORT = process.env.EXPO_PUBLIC_API_PORT || '8080';

/**
 * The NexHome backend as this device reaches it:
 * 1. EXPO_PUBLIC_API_BASE_URL (.env) when set (a fixed server, release builds);
 * 2. while developing, the computer serving the app (Metro's address, e.g. 192.168.29.230:8081) on the backend's
 *    port, so any phone or emulator on the same network works without changing anything;
 * 3. else the Android emulator's address of the computer, 10.0.2.2.
 */
function resolveApiBaseUrl() {
  const configured = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (configured) return configured.replace(/\/+$/, '');
  const hostUri = Constants.expoConfig?.hostUri || Constants.expoGoConfig?.debuggerHost || '';
  const host = hostUri.split(':')[0];
  // A LAN address or localhost (adb reverse); a tunnel's domain cannot reach the backend
  if (host && (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host === 'localhost')) return `http://${host}:${API_PORT}`;
  return `http://10.0.2.2:${API_PORT}`;
}

export const API_BASE_URL = resolveApiBaseUrl();

/** The backend's error, with its ExceptionBody message (client-safe text) and status. */
export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

/** An address the backend gives as a path (/api/...) as a full URL; full URLs pass through; null stays null. */
export function fileUrl(path) {
  if (!path) return null;
  return /^https?:\/\//i.test(path) ? path : `${API_BASE_URL}${path.startsWith('/') ? '' : '/'}${path}`;
}

/** One request: JSON in and out, the bearer token when given; non-2xx responses throw ApiError. */
export async function apiRequest(path, { method = 'GET', body, token } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw new ApiError('Cannot reach NexHome right now. Check your internet connection and try again.', 0);
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!response.ok) {
    const message = (data && typeof data === 'object' && data.message) || 'Something went wrong. Please try again.';
    throw new ApiError(message, response.status, data);
  }
  return data;
}
