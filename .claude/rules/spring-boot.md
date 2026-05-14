# Rule: Spring Boot

## Version

Spring Boot 3.4+, Java 21+ or Kotlin 2.x. Use virtual threads (Project Loom) for I/O-bound work.

## Application Properties

Use YAML (`application.yml`). Split into profiles:
- `application.yml` — shared non-sensitive config
- `application-local.yml` — local development overrides (gitignored if it contains secrets)
- `application-test.yml` — test overrides (Testcontainers URLs, reduced pool sizes)

Never commit secrets. Use Spring Cloud Config or Kubernetes secrets for production values.

## Bean Configuration

Prefer constructor injection over field injection. Never use `@Autowired` on fields in Kotlin — use constructor injection or `@Autowired` on the constructor (implicit with single-constructor data class):

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val featureProperties: FeatureProperties,
)
```

## Transaction Boundaries

Transactions belong on the service layer, not the controller or repository:

```kotlin
@Service
@Transactional(readOnly = true)  // default to read-only for service
class UserService {
    @Transactional  // override for writes
    fun createUser(command: CreateUserCommand): User { ... }
}
```

Avoid `@Transactional` on controllers.

## Flyway

Use Flyway for all database migrations:

```
db/migration/
  V1__initial_schema.sql
  V2__add_users_table.sql
  V3__add_user_roles.sql
```

- Never modify an existing migration file.
- Test migrations in CI against a real database (Testcontainers).
- Include `-- rollback:` comment blocks for manual rollback documentation.

## Spring Security

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }  // stateless JWT — no CSRF needed
            .sessionManagement { it.sessionCreationPolicy(STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            .build()
}
```

## Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
  endpoint:
    health:
      show-details: when-authorized
  info:
    git:
      mode: full
```

Health endpoint must aggregate all dependency health (DB, Redis, downstream services).

## Async Processing

```kotlin
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean
    fun applicationTaskExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 5
        maxPoolSize = 20
        queueCapacity = 100
        setThreadNamePrefix("async-")
        initialize()
    }
}

@Service
class NotificationService {
    @Async
    fun sendEmail(to: Email, subject: String) { ... }
}
```

Prefer Kotlin coroutines over `@Async` in new Kotlin services.

## Testing

See `test-pyramid.md` and `/spring-boot-patterns` for integration test patterns.

Always use `@Testcontainers` for integration tests — never use an embedded H2 database (it hides SQL dialect differences).
