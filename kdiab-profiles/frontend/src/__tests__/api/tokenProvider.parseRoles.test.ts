import { describe, it, expect } from 'vitest';
import { parseRolesFromToken } from '../../api/tokenProvider';

function buildToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fakesig`;
}

describe('parseRolesFromToken', () => {
  it('returns [] for empty roles array', () => {
    expect(parseRolesFromToken(buildToken({ roles: [] }))).toEqual([]);
  });

  it('returns uppercased roles from direct roles claim', () => {
    const roles = parseRolesFromToken(buildToken({ roles: ['admin', 'doctor'] }));
    expect(roles).toContain('ADMIN');
    expect(roles).toContain('DOCTOR');
  });

  it('returns roles from realm_access.roles', () => {
    const roles = parseRolesFromToken(buildToken({ realm_access: { roles: ['PATIENT'] } }));
    expect(roles).toContain('PATIENT');
  });

  it('returns roles from resource_access', () => {
    const roles = parseRolesFromToken(buildToken({
      resource_access: { 'my-client': { roles: ['ADMIN'] } }
    }));
    expect(roles).toContain('ADMIN');
  });

  it('rejects scalar string roles claim', () => {
    expect(parseRolesFromToken(buildToken({ roles: 'ADMIN' }))).toEqual([]);
  });

  it('returns [] when roles claim is absent', () => {
    expect(parseRolesFromToken(buildToken({}))).toEqual([]);
  });

  it('returns [] for a malformed token', () => {
    expect(parseRolesFromToken('not.a.token')).toEqual([]);
  });
});
