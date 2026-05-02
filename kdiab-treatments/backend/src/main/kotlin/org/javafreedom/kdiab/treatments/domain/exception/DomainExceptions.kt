package org.javafreedom.kdiab.treatments.domain.exception

class AuthenticationException(message: String = "Authentication failed") :
        RuntimeException(message)

class AuthorizationException(message: String = "Access denied") : RuntimeException(message)

class ResourceNotFoundException(message: String = "Resource not found") : RuntimeException(message)

class BusinessValidationException(message: String) : RuntimeException(message)

/** Thrown when an operation conflicts with a concurrent operation. */
class ConflictException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
