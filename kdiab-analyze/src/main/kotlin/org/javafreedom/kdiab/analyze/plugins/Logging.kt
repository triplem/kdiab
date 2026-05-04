package org.javafreedom.kdiab.analyze.plugins

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
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        replyToHeader("X-Correlation-ID")
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("Correlation-ID")
    }
}
