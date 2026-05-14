# Kotlin Logging Reference

## Logback JSON config (production)

```xml
<!-- logback-spring.xml -->
<configuration>
  <springProfile name="!local">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>
  <springProfile name="local">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder><pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern></encoder>
    </appender>
    <root level="DEBUG"><appender-ref ref="CONSOLE"/></root>
  </springProfile>
</configuration>
```

## MDC correlation filter

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcRequestFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val traceId = req.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        MDC.put("traceId", traceId)
        res.addHeader("X-Trace-Id", traceId)
        try { chain.doFilter(req, res) } finally { MDC.clear() }
    }
}
```

## Service logging pattern

```kotlin
fun createUser(cmd: CreateUserCommand): User {
    logger.info { "createUser_start email=${cmd.email}" }
    return try {
        repo.save(User.create(cmd)).also { logger.info { "createUser_success userId=${it.id}" } }
    } catch (e: UserAlreadyExistsException) {
        logger.warn { "createUser_conflict email=${cmd.email}" }; throw e
    } catch (e: Exception) {
        logger.error(e) { "createUser_error email=${cmd.email}" }; throw e
    }
}
```
