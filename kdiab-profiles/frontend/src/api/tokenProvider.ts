/**
 * In-memory token store populated by the React auth layer (App.tsx via useAuth).
 * Keeping the token here instead of reading sessionStorage directly in the axios
 * interceptor avoids coupling the HTTP client to a hardcoded storage key and makes
 * it easier to change the storage mechanism in the future.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/**
 * Decodes a JWT payload (no signature verification — for UI display only).
 * The backend is the authoritative validator of the token signature.
 */
function decodeJwtPayload(token: string): Record<string, unknown> {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return {};
  }
}

/**
 * Extracts the allowed_patients claim from a JWT access token.
 * Returns an empty array if the claim is absent or malformed.
 * For UI display only — the backend enforces the actual authorisation.
 */
export function parseAllowedPatientsFromToken(token: string): string[] {
  const payload = decodeJwtPayload(token);
  const val = payload['allowed_patients'];
  return Array.isArray(val) ? val.filter((v): v is string => typeof v === 'string') : [];
}

/**
 * Extracts roles from a JWT access token.
 * Only accepts actual arrays of strings — rejects scalar claims to prevent
 * crafted tokens from gaining UI-level role elevation.
 */
export function parseRolesFromToken(token: string): string[] {
  const strictArray = (val: unknown): string[] =>
    Array.isArray(val) ? val.filter((r): r is string => typeof r === 'string') : [];

  const payload = decodeJwtPayload(token);
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
  const resourceAccess = payload.resource_access as Record<string, { roles?: unknown }> | undefined;

  return [
    ...strictArray(payload.roles),
    ...strictArray(realmAccess?.roles),
    ...(Object.values(resourceAccess ?? {}).flatMap(c => strictArray((c as Record<string, unknown>)?.roles))),
  ].map(r => r.toUpperCase());
}
