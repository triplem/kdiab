# TypeScript Logging Reference

## Pino setup (Node.js)

```typescript
// src/logger.ts
import pino from 'pino';
export const logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  ...(process.env.NODE_ENV === 'development' ? { transport: { target: 'pino-pretty' } } : {}),
  base: { service: process.env.SERVICE_NAME, env: process.env.NODE_ENV },
  redact: {
    paths: ['*.password','*.token','*.secret','*.authorization','*.email'],
    censor: '[REDACTED]',
  },
});
```

## Express middleware

```typescript
import pinoHttp from 'pino-http';
app.use(pinoHttp({
  logger,
  customLogLevel: (_req, res) => res.statusCode >= 500 ? 'error' : res.statusCode >= 400 ? 'warn' : 'info',
  serializers: {
    req: (req) => ({ method: req.method, url: req.url, id: req.id }),
    res: (res) => ({ statusCode: res.statusCode }),
  },
}));
```

## Service pattern

```typescript
class UserService {
  constructor(private readonly repo: UserRepository, private readonly log: pino.Logger) {}

  async createUser(dto: CreateUserDto): Promise<User> {
    this.log.info({ email: dto.email }, 'createUser_start');
    try {
      const user = await this.repo.create(dto);
      this.log.info({ userId: user.id }, 'createUser_success');
      return user;
    } catch (err) {
      this.log.error({ err, email: dto.email }, 'createUser_failed');
      throw err;
    }
  }
}
```

## React frontend logging

```typescript
// src/lib/logger.ts
export const logger = {
  warn: (ctx: object, msg: string) => console.warn(JSON.stringify({ level: 'warn', ...ctx, msg })),
  error: (ctx: object, msg: string) => {
    console.error(JSON.stringify({ level: 'error', ...ctx, msg }));
    // Send to observability endpoint (Sentry, Datadog, etc.)
  },
};
```
