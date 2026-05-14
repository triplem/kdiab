# Kotlin Patterns Reference

## Value Objects

```kotlin
@JvmInline value class UserId(val value: Long)
@JvmInline value class Email(val value: String) {
    init { require(value.contains("@")) { "Invalid email: $value" } }
}
```

## Sealed Results

```kotlin
sealed class UserResult {
    data class Found(val user: User) : UserResult()
    data class NotFound(val id: UserId) : UserResult()
    data class ValidationError(val message: String) : UserResult()
}

fun findUser(id: UserId): UserResult = when (val user = repo.findById(id)) {
    null -> UserResult.NotFound(id)
    else -> UserResult.Found(user)
}
```

## Coroutines — Parallel

```kotlin
suspend fun getUserDashboard(id: UserId): Dashboard = coroutineScope {
    val user = async { userRepo.findById(id) ?: throw EntityNotFoundException("User $id") }
    val stats = async { statsRepo.findByUserId(id) }
    Dashboard(user.await(), stats.await())
}
```

## Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    id("io.gitlab.artefacts.detekt") version "1.23.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    jacoco
}
tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.80".toBigDecimal() } } }
}
```
