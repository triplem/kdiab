package org.javafreedom.kdiab.common.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.ktor.http.Headers
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.util.*

private val logger = KotlinLogging.logger {}

private val tracingSpanKey = AttributeKey<io.opentelemetry.api.trace.Span>("otel-span")

private object KtorHeadersGetter : TextMapGetter<Headers> {
    override fun keys(carrier: Headers): Iterable<String> = carrier.names()
    override fun get(carrier: Headers?, key: String): String? = carrier?.get(key)
}

/**
 * Initialises the OpenTelemetry SDK via autoconfigure and installs a Ktor plugin that
 * creates a server span per request and propagates W3C trace context.
 *
 * All configuration is via standard OTel environment variables:
 *   OTEL_SERVICE_NAME          — e.g. kdiab-analyze
 *   OTEL_EXPORTER_OTLP_ENDPOINT — e.g. http://otel-collector:4318
 *   OTEL_TRACES_EXPORTER       — e.g. otlp (default: none = no-op)
 *
 * When OTEL_TRACES_EXPORTER=none (the default) this is a zero-overhead no-op.
 */
fun Application.configureTracing() {
    val otel: OpenTelemetry = try {
        AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk.also {
            logger.info {
                "OTel tracing initialised service=${System.getenv("OTEL_SERVICE_NAME") ?: "unknown"} " +
                "exporter=${System.getenv("OTEL_TRACES_EXPORTER") ?: "none"}"
            }
        }
    } catch (e: Exception) {
        // GlobalOpenTelemetry is a JVM-level singleton. In tests multiple Ktor applications
        // start in the same JVM, so every application after the first hits this path.
        // That is expected — log at DEBUG so test output stays clean.
        val alreadySet = generateSequence<Throwable>(e) { it.cause }
            .any { it is IllegalStateException && it.message?.contains("already been called") == true }
        if (alreadySet) {
            logger.debug { "OTel already initialised in this JVM — skipping duplicate registration" }
        } else {
            logger.warn(e) { "OpenTelemetry autoconfigure failed — tracing disabled" }
        }
        return
    }

    val tracer = otel.getTracer("org.javafreedom.kdiab")
    val propagator = otel.propagators.textMapPropagator

    install(createApplicationPlugin("KdiabOtelTracing") {
        onCall { call ->
            val parentContext = propagator.extract(
                Context.current(),
                call.request.headers,
                KtorHeadersGetter,
            )
            val span = tracer.spanBuilder(call.request.path())
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext)
                .setAttribute("http.method", call.request.httpMethod.value)
                .setAttribute("http.url", call.request.uri)
                .setAttribute("correlation.id", call.callId ?: "")
                .startSpan()
            call.attributes.put(tracingSpanKey, span)
        }

        onCallRespond { call, _ ->
            val span = call.attributes.getOrNull(tracingSpanKey) ?: return@onCallRespond
            val statusCode = call.response.status()?.value ?: 0
            span.setAttribute("http.status_code", statusCode.toLong())
            if (statusCode >= HTTP_SERVER_ERROR_THRESHOLD) span.setStatus(StatusCode.ERROR)
            span.end()
        }
    })
}

private const val HTTP_SERVER_ERROR_THRESHOLD = 500
