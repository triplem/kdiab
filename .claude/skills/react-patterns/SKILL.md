---
name: react-patterns
description: React 18+ hooks, component design, state management with TanStack Query and Zustand, and performance patterns. Apply when writing or reviewing React code.
when_to_use: Apply when implementing or reviewing React components, hooks, or React applications.
user-invocable: false
paths: "**/*.tsx,**/*.jsx"
---

Apply these patterns when writing React code.

## Component hierarchy

Split components: data-fetching → state-owning → presentational.

## State rules

- Local UI state → `useState`
- Derived state → compute inline (not state)
- Server state → TanStack Query (never `useEffect` for fetching)
- Global client state → Zustand
- Forms → React Hook Form + Zod

## Hooks discipline

- Data fetching in custom hooks, never in components directly
- `takeUntilDestroyed` / cleanup in `useEffect` return
- `useCallback` only for callbacks passed to memoized children
- `useMemo` only for demonstrably expensive computations
- `memo()` only for components that re-render unnecessarily with stable props

## Testing

- React Testing Library: test user-visible behaviour, not implementation
- MSW for API mocking in tests
- `screen.findByText` for async assertions (not `getByText` + `waitFor`)

## Anti-patterns

- No business logic in components — move to custom hooks or services
- No `useEffect` for data fetching — TanStack Query
- No prop drilling beyond 2 levels
- No `index` as key in dynamic lists
- No inline object/function props on frequently rendered components
