---
name: angular-patterns
description: Angular 17+ standalone components, signals, reactive forms, and RxJS patterns. Apply when writing or reviewing Angular code.
when_to_use: Apply when implementing or reviewing Angular components, services, or Angular applications.
user-invocable: false
paths: "**/*.component.ts,**/*.component.html,**/*.service.ts,**/app.config.ts"
---

Apply these patterns when writing Angular code.

## Core rules

- Standalone components (Angular 17+) — no NgModule for new code
- Use signals for reactive state — not `BehaviorSubject` for new code
- `inject()` over constructor injection in components
- New control flow syntax (`@if`, `@for`, `@switch`) — not `*ngIf`, `*ngFor`

## State

- `signal()` + `computed()` for component and store state
- `toSignal()` to convert observables to signals in templates
- `effect()` sparingly — only for side effects, not derived state

## RxJS

- Keep RxJS in services. Surface to components as signals via `toSignal()`.
- `takeUntilDestroyed()` on every subscription in components
- `shareReplay(1)` for shared HTTP observables

## Forms

Typed reactive forms with `fb.nonNullable.group()`. Validate with Angular validators + custom validators. Never template-driven forms for complex forms.

## Routing

Lazy-load all feature routes. Route guards as functional guards (not class-based).

## Anti-patterns

- No logic in templates beyond simple conditionals
- No direct DOM manipulation
- No subscriptions without `takeUntilDestroyed()` or `async` pipe
- No `ngOnInit` for data loading backed by a signal store
