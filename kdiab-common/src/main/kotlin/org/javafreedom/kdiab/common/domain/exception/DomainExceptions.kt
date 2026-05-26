package org.javafreedom.kdiab.common.domain.exception

class AuthenticationException(message: String = "Authentication failed") : RuntimeException(message)

class AuthorizationException(message: String = "Access denied") : RuntimeException(message)

class ResourceNotFoundException(message: String = "Resource not found") : RuntimeException(message)

class BusinessValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown when an operation conflicts with a concurrent operation. */
class ConflictException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown when a client exceeds the configured request rate limit. */
class RateLimitExceededException(
    val retryAfterSeconds: Long,
    val limit: Int,
    message: String = "Too many requests",
) : RuntimeException(message)
