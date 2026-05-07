package org.javafreedom.kdiab.treatments.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureLogging() {
    val suppressHealthChecks = environment.config
        .propertyOrNull("logging.suppressHealthChecks")
        ?.getString()?.toBooleanStrictOrNull() ?: true

    install(CallId) {
        header("X-Correlation-ID")
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        replyToHeader("X-Correlation-ID")
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("Correlation-ID")
        if (suppressHealthChecks) {
            filter { call ->
                val path = call.request.path()
                !path.endsWith("/healthz") && !path.endsWith("/readyz")
            }
        }
    }
}
