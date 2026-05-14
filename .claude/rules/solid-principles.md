# Rule: SOLID & Clean Code

## Single Responsibility Principle (SRP)

A class or function has one reason to change.

**Violation signals:**
- Class name contains "And", "Or", "Manager", "Helper", "Utils"
- Method is > 20 lines
- Class has > 5 public methods
- Class imports from > 3 different domains

**Fix:** Extract to focused classes. One class = one domain concept.

## Open/Closed Principle (OCP)

Open for extension, closed for modification. Add behaviour by adding code, not changing existing code.

**Pattern:** Strategy, Decorator, Extension functions (Kotlin), extension methods (C#).

```kotlin
// BAD — must modify this to add new discount type
fun calculateDiscount(order: Order, type: String): BigDecimal = when (type) {
    "SEASONAL" -> order.total * 0.1.toBigDecimal()
    "LOYALTY" -> order.total * 0.15.toBigDecimal()
    else -> BigDecimal.ZERO
}

// GOOD — add new types without modifying existing code
interface DiscountStrategy { fun calculate(order: Order): BigDecimal }
class SeasonalDiscount : DiscountStrategy { override fun calculate(order: Order) = order.total * BigDecimal("0.10") }
class LoyaltyDiscount : DiscountStrategy { override fun calculate(order: Order) = order.total * BigDecimal("0.15") }
```

## Liskov Substitution Principle (LSP)

Subclasses must be usable wherever the parent is used without breaking behaviour.

**Violation signals:**
- Overriding a method by throwing `UnsupportedOperationException`
- Narrowing preconditions or widening postconditions in an override
- `instanceof` checks before calling a method

## Interface Segregation Principle (ISP)

Clients should not depend on interfaces they don't use.

**Fix:** Split large interfaces into role-specific ones. A `UserRepository` should not have both read methods and admin-only bulk-delete methods on the same interface.

## Dependency Inversion Principle (DIP)

High-level modules depend on abstractions, not concretions.

```kotlin
// BAD — UserService depends on the concrete class
class UserService(private val repo: PostgresUserRepository)

// GOOD — UserService depends on the abstraction
class UserService(private val repo: UserRepository)
interface UserRepository { fun findById(id: UserId): User? }
class PostgresUserRepository : UserRepository { ... }
```

Always inject interfaces, not concrete classes. Register implementations in DI container.

## Clean Code Rules

### Functions / Methods

- Do one thing.
- Max 20 lines. If longer, extract.
- Parameters: max 3. If more, use a parameter object.
- No side effects from a function named like a query (`getUser` should not write to the DB).
- Boolean parameters are a design smell — split into two methods.

### Naming

- Classes: nouns (`UserService`, `OrderProcessor`).
- Methods: verbs (`findById`, `calculateTotal`, `sendEmail`).
- Booleans: `is`, `has`, `can`, `should` prefix (`isActive`, `hasPermission`).
- Constants: SCREAMING_SNAKE_CASE.
- No abbreviations unless universally known (`id`, `url`, `http`, `dto`).
- Names should be self-documenting — avoid `temp`, `data`, `info`, `result` as sole names.

### Comments

Only write a comment when the WHY is non-obvious. The code says what; the comment says why.

```kotlin
// BAD — obvious what, no why
// increment the counter
count++

// GOOD — explains a non-obvious business rule
// Retry limit is 3 per PCI-DSS requirement 6.4.2
val MAX_AUTH_RETRIES = 3
```

### Error Handling

- Use domain-specific exceptions (`UserNotFoundException` not `RuntimeException("user not found")`).
- Catch at the boundary (controller, message consumer), not deep in business logic.
- Never swallow exceptions silently.
- Never return null from a method that should always return a value — use `Optional`, `Result`, or sealed types.

### Magic Numbers / Strings

All magic values must be named constants:

```kotlin
// BAD
if (attempts > 3) lock()

// GOOD
const val MAX_LOGIN_ATTEMPTS = 3
if (attempts > MAX_LOGIN_ATTEMPTS) lock()
```
