# Rule: Test Pyramid

Tests must follow the test pyramid: many fast unit tests at the base, fewer slower integration tests, minimal E2E tests at the top.

## Pyramid Ratios (per story)

```
         ▲ E2E / Acceptance (1–3)
        ▲▲▲ Integration / Contract (5–15)
       ▲▲▲▲▲ Unit (20–100+)
```

## Layer Definitions

### Unit Tests

- Test a single class/function in isolation.
- All dependencies are mocked/stubbed.
- No I/O: no database, no network, no filesystem.
- Fast: each test < 10ms.
- High count: test every public method, every branch.

**What to test:**
- Business rules and calculations
- Domain logic
- Validation
- Error conditions and edge cases
- Pure functions

**What NOT to test at this layer:**
- Framework wiring
- Database queries
- HTTP routing

### Integration Tests

- Test collaboration between 2+ real components.
- Use Testcontainers for database/messaging.
- May use real HTTP (within the service).
- Slower: each test < 2s.

**What to test:**
- Repository queries against real DB
- Service + repository interaction
- Controller + service (in-process, no HTTP)
- Message consumer + handler

### Contract Tests

- Verify API contracts between services.
- Use Pact (consumer-driven) or Spring Cloud Contract.
- Run against real consumer/provider, not mocks.

**When required:**
- Any service that exposes an API consumed by another service.
- Any service that consumes an external API.

### E2E / Acceptance Tests

- Test the full system from the user's perspective.
- Use Playwright (web), REST API calls, or Cucumber scenarios.
- Slow: each test may take 5–30s.
- Keep the count small — test the golden path + top 2–3 critical edge cases.
- Must map 1:1 to story acceptance criteria.

**What to test:**
- Critical user journeys (happy path)
- Business-critical error scenarios
- Cross-service workflows

## Tools by Stack

| Stack | Unit | Integration | E2E |
|---|---|---|---|
| Kotlin/Spring | JUnit 5 + Mockk | SpringBootTest + Testcontainers | Playwright / REST Assured |
| TypeScript/Node | Jest / Vitest + ts-mockito | Supertest + Testcontainers | Playwright |
| React | Jest + RTL | MSW | Playwright |
| Angular | Jest + TestBed | TestBed + MSW | Playwright / Cypress |
| .NET | xUnit + Moq | WebApplicationFactory + Testcontainers | Playwright |

## Test Naming

```
should_<expected>_when_<condition>

// Kotlin
@Test fun `should throw UserNotFound when id does not exist`()

// TypeScript
it('should return 404 when user id does not exist')

// C#
[Fact] public void ShouldThrowUserNotFound_WhenIdDoesNotExist()
```

## Test Data

- Use builders or factories, not raw object literals (prevents brittle tests).
- Use `@BeforeEach` / `beforeEach` to reset state between tests.
- Never share mutable state between test cases.
- Use realistic-looking fake data (faker.js, JavaFaker) not `"test"` / `1`.

## Coverage Targets

| Type | Target |
|---|---|
| Line coverage | ≥ 80% |
| Branch coverage | ≥ 75% |
| New code only (brownfield) | ≥ 80% |

Coverage is a floor, not a goal. 80% of meaningful code is better than 90% of trivial getters.
