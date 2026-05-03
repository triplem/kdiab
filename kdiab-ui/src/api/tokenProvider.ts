import type { AxiosInstance } from 'axios'

let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

function decodeJwtPayload(token: string): Record<string, unknown> {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, '=')
    return JSON.parse(atob(padded)) as Record<string, unknown>
  } catch {
    return {}
  }
}

function strictArray(val: unknown): string[] {
  return Array.isArray(val) ? val.filter((r): r is string => typeof r === 'string') : []
}

export function parseRolesFromToken(token: string): string[] {
  const payload = decodeJwtPayload(token)
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined
  const resourceAccess = payload.resource_access as Record<string, { roles?: unknown }> | undefined
  return [
    ...strictArray(payload.roles),
    ...strictArray(realmAccess?.roles),
    ...Object.values(resourceAccess ?? {}).flatMap((c) =>
      strictArray((c as Record<string, unknown>)?.roles),
    ),
  ].map((r) => r.toUpperCase())
}

export function parseAllowedPatientsFromToken(token: string): string[] {
  const payload = decodeJwtPayload(token)
  const val = payload['allowed_patients']
  return Array.isArray(val) ? val.filter((v): v is string => typeof v === 'string') : []
}

export function parseGlucoseUnitFromToken(token: string): string {
  const payload = decodeJwtPayload(token)
  const val = payload['glucose_unit']
  return typeof val === 'string' ? val : 'mg/dL'
}

export function parseWeightUnitFromToken(token: string): string {
  const payload = decodeJwtPayload(token)
  const val = payload['weight_unit']
  return typeof val === 'string' ? val : 'kg'
}

export function configureAuthInterceptor(instance: AxiosInstance): void {
  instance.interceptors.request.use((config) => {
    if (accessToken) config.headers['Authorization'] = `Bearer ${accessToken}`
    return config
  })
}
