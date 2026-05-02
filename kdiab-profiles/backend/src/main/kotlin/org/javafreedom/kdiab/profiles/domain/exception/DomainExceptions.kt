package org.javafreedom.kdiab.profiles.domain.exception

class AuthenticationException(message: String = "Authentication failed") :
        RuntimeException(message)

class AuthorizationException(message: String = "Access denied") : RuntimeException(message)

class ResourceNotFoundException(message: String = "Resource not found") : RuntimeException(message)

class BusinessValidationException(message: String) : RuntimeException(message)

class DomainException(message: String) : RuntimeException(message)

/** Thrown when a state transition conflicts with a concurrent operation (e.g. two simultaneous activations). */
class ConflictException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
