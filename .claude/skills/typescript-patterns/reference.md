# TypeScript Patterns Reference

## Branded Types

```typescript
type Brand<T, B> = T & { readonly _brand: B };
type UserId = Brand<number, 'UserId'>;
type Email = Brand<string, 'Email'>;
const createEmail = (raw: string): Email => {
  if (!raw.includes('@')) throw new Error(`Invalid email: ${raw}`);
  return raw as Email;
};
```

## Result Type

```typescript
type Result<T, E = Error> = { ok: true; value: T } | { ok: false; error: E };
const ok = <T>(value: T): Result<T> => ({ ok: true, value });
const err = <E>(error: E): Result<never, E> => ({ ok: false, error });

async function findUser(id: UserId): Promise<Result<User, 'NOT_FOUND'>> {
  const user = await db.users.findById(id);
  return user ? ok(user) : err('NOT_FOUND');
}
```

## Discriminated Unions

```typescript
type UserEvent =
  | { type: 'CREATED'; user: User; timestamp: Date }
  | { type: 'UPDATED'; userId: UserId; changes: Partial<User> }
  | { type: 'DELETED'; userId: UserId };

function handle(event: UserEvent) {
  switch (event.type) {
    case 'CREATED': return onCreated(event.user);
    case 'UPDATED': return onUpdated(event.userId, event.changes);
    case 'DELETED': return onDeleted(event.userId);
  }
}
```

## tsconfig (required settings)

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "noImplicitReturns": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true
  }
}
```
