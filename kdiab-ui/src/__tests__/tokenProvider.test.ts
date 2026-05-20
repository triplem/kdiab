import { describe, test, expect } from 'vitest'
import {
  parseRolesFromToken,
  parseAllowedPatientsFromToken,
} from '../api/tokenProvider'

function buildJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  return `${header}.${body}.fakesig`
}

describe('parseRolesFromToken', () => {
  test('returns roles array from token', () => {
    const token = buildJwt({ roles: ['PATIENT', 'ADMIN'] })
    expect(parseRolesFromToken(token)).toEqual(['PATIENT', 'ADMIN'])
  })

  test('returns empty array when roles is missing', () => {
    const token = buildJwt({})
    expect(parseRolesFromToken(token)).toEqual([])
  })

  test('returns empty array when roles is a scalar string (injection attempt)', () => {
    const token = buildJwt({ roles: 'ADMIN' })
    expect(parseRolesFromToken(token)).toEqual([])
  })
})

describe('parseAllowedPatientsFromToken', () => {
  test('returns patient IDs for doctor', () => {
    const ids = ['aaa-bbb', 'ccc-ddd']
    const token = buildJwt({ allowed_patients: ids })
    expect(parseAllowedPatientsFromToken(token)).toEqual(ids)
  })

  test('returns empty array when claim absent', () => {
    const token = buildJwt({})
    expect(parseAllowedPatientsFromToken(token)).toEqual([])
  })
})

