---
name: kotlin-patterns
description: Kotlin idioms, coroutines, sealed classes, and JVM best practices. Apply when writing or reviewing Kotlin code.
when_to_use: Apply when implementing, reviewing, or discussing Kotlin code. Triggered by .kt files, Kotlin-specific questions, or Gradle Kotlin DSL.
user-invocable: false
paths: "**/*.kt,**/build.gradle.kts"
---

Apply these patterns when writing Kotlin code. See `reference.md` for detailed examples.

## Core idioms

- Use `val` over `var`. A `var` requires justification.
- Prefer `@JvmInline value class` for single-field primitives (eliminate primitive obsession)
- Use sealed classes for domain results — never throw for expected outcomes
- Use extension functions to add behaviour without inheritance
- `when` over `if-else if` chains
- Use scope functions correctly: `let` (nullable transform), `apply` (configure), `also` (side effect), `run` (compute result)

## Null safety

- Never `!!` in production — use `?: throw` with descriptive message or `requireNotNull`
- Early return on null: `val user = repo.findById(id) ?: return Result.NotFound(id)`

## Coroutines

- Never `GlobalScope`. Always structured concurrency.
- `Dispatchers.IO` for blocking I/O in coroutines.
- `coroutineScope { async { } + async { } }` for parallel independent calls.

## Anti-patterns

- No `lateinit var` except for framework injection
- No mutable `var` in data classes — use `copy()`
- No Java-style checked exception patterns — use sealed results
- No blocking I/O without `Dispatchers.IO`

For detailed examples → `reference.md`
