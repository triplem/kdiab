package org.javafreedom.kdiab.common.plugins

/**
 * Default values for outbound HTTP client configuration.
 *
 * These constants are shared by every kdiab service that makes upstream HTTP calls
 * (kdiab-analyze, kdiab-calc, …) so that sane defaults are defined in one place.
 * Each service can override them via its application configuration (e.g. http.connectTimeoutMs).
 */
const val HTTP_SERVER_ERROR_STATUS = 500
const val HTTP_CONNECT_TIMEOUT_MS_DEFAULT = 5_000L
const val HTTP_REQUEST_TIMEOUT_MS_DEFAULT = 10_000L
const val HTTP_SOCKET_TIMEOUT_MS_DEFAULT = 5_000L
const val HTTP_RETRY_MAX_RETRIES_DEFAULT = 3
const val HTTP_RETRY_MAX_DELAY_MS_DEFAULT = 8_000L
