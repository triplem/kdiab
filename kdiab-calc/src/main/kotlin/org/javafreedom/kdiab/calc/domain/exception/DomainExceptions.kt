package org.javafreedom.kdiab.calc.domain.exception

class AuthenticationException(message: String = "Authentication failed") : RuntimeException(message)

class AuthorizationException(message: String = "Access denied") : RuntimeException(message)

class ResourceNotFoundException(message: String = "Resource not found") : RuntimeException(message)

class BusinessValidationException(message: String) : RuntimeException(message)

class ConflictException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
