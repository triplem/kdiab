---
name: typescript-patterns
description: TypeScript strict-mode patterns, branded types, Result types, and module design. Apply when writing or reviewing TypeScript or JavaScript code.
when_to_use: Apply when implementing, reviewing, or discussing TypeScript/JavaScript code. Triggered by .ts, .tsx files.
user-invocable: false
paths: "**/*.ts,**/*.tsx"
---

Apply these patterns when writing TypeScript. See `reference.md` for detailed examples.

## Core rules

- `strict: true` + `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes` — always
- Use branded types for primitive values (prevent wrong-type substitution)
- Use `Result<T, E>` sealed type — never throw for expected business outcomes
- Use discriminated unions for state variants
- No `any` — use `unknown` and narrow
- No `export default` — named exports only
- No `==` — always `===`

## Runtime validation

Use Zod at system boundaries (HTTP handlers, env vars, config):
```typescript
const CreateUserSchema = z.object({ email: z.string().email(), name: z.string().min(2) });
type CreateUserDto = z.infer<typeof CreateUserSchema>;
```

## Async

Always `await` Promises. Never ignore with `void` without an explicit `catch` handler.
Parallel independent calls: `Promise.all([...])`.

## Module structure

One barrel `index.ts` per feature folder with explicit named exports. Internal types are not re-exported.

## Anti-patterns

- No `as` cast unless type is verified
- No unhandled `Promise`
- No mutation of function arguments
- No index as key in dynamic lists (React)

For detailed examples → `reference.md`
