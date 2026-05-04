package org.javafreedom.kdiab.profiles.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureLogging() {
    install(CallId) {
        header("X-Correlation-ID")
        // Generate a new UUID if the header is not present
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        // Always include the Correlation ID in the response
        replyToHeader("X-Correlation-ID")
    }

    install(CallLogging) {
        level = Level.INFO
        // Put the callId into the SLF4J MDC using the key "Correlation-ID"
        callIdMdc("Correlation-ID")
    }
}
