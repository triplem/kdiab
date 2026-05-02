import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { setAccessToken } from '../../api/tokenProvider';
import { axiosInstance } from '../../api/client';

describe('axiosInstance request interceptor', () => {
  beforeEach(() => setAccessToken(null));
  afterEach(() => setAccessToken(null));

  function runInterceptor(initialHeaders: Record<string, string> = {}) {
    // axiosInstance.interceptors.request.handlers is the internal array of { fulfilled, rejected }
    // objects. We call the fulfilled handler directly to test what it does to config.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const handlers = (axiosInstance.interceptors.request as any).handlers as Array<{
      fulfilled: (cfg: unknown) => unknown;
    } | null>;
    const handler = handlers.find((h) => h !== null);
    if (!handler) throw new Error('No request interceptor found');
    return handler.fulfilled({ headers: { ...initialHeaders } }) as { headers: Record<string, string> };
  }

  it('adds no Authorization header when no token is set', () => {
    const config = runInterceptor();
    expect(config.headers['Authorization']).toBeUndefined();
  });

  it('adds Bearer Authorization header when a token is set', () => {
    setAccessToken('test-jwt-token');
    const config = runInterceptor();
    expect(config.headers['Authorization']).toBe('Bearer test-jwt-token');
  });

  it('adds an X-Correlation-ID header to every request', () => {
    const config = runInterceptor();
    expect(config.headers['X-Correlation-ID']).toBeTruthy();
  });

  it('does NOT overwrite an existing X-Correlation-ID', () => {
    const config = runInterceptor({ 'X-Correlation-ID': 'existing-id' });
    expect(config.headers['X-Correlation-ID']).toBe('existing-id');
  });
});
