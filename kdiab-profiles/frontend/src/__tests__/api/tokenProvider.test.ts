import { describe, it, expect, beforeEach } from 'vitest';
import { getAccessToken, setAccessToken } from '../../api/tokenProvider';

describe('tokenProvider', () => {
  beforeEach(() => {
    // Reset to null between tests
    setAccessToken(null);
  });

  it('returns null when no token has been set', () => {
    expect(getAccessToken()).toBeNull();
  });

  it('returns the token after setAccessToken is called', () => {
    setAccessToken('my-jwt-token');
    expect(getAccessToken()).toBe('my-jwt-token');
  });

  it('overwrites a previous token', () => {
    setAccessToken('first-token');
    setAccessToken('second-token');
    expect(getAccessToken()).toBe('second-token');
  });

  it('clears the token when set to null', () => {
    setAccessToken('some-token');
    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });
});
