---
name: logging-typescript
description: Structured logging with Pino for Node.js/TypeScript services and React frontends. Apply when writing or reviewing TypeScript logging code.
when_to_use: Apply when adding logging to TypeScript/Node.js code, configuring Pino, or reviewing log statements.
user-invocable: false
paths: "**/*.ts,**/*.js,**/logger.ts"
---

Apply these logging patterns in all TypeScript/Node.js code. See `reference.md` for Pino setup details.

## Setup

```bash
npm install pino pino-http
npm install -D pino-pretty  # local only
```

```typescript
// src/logger.ts — one instance, imported everywhere
export const logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  ...(process.env.NODE_ENV === 'development' ? { transport: { target: 'pino-pretty' } } : {}),
  base: { service: process.env.SERVICE_NAME },
  redact: { paths: ['*.password','*.token','*.secret','*.authorization'], censor: '[REDACTED]' },
});
```

## Structured fields — always

```typescript
// WRONG
logger.info(`User ${userId} logged in`);

// RIGHT
logger.info({ userId, ip }, 'user_login');

// ERROR — pass err as first arg or in context
logger.error({ err, orderId }, 'payment_failed');
```

## Child loggers per request

```typescript
req.log = logger.child({ requestId: req.headers['x-request-id'] ?? crypto.randomUUID() });
```

Pass child logger down the call chain, not the root logger.

## Levels

Same as Kotlin guide. Production default: `info`.

## Never log

Passwords · tokens · PII · full request/response bodies in production.

For React frontend logging and Pino HTTP middleware → `reference.md`
