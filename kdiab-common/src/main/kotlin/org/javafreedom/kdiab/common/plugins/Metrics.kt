package org.javafreedom.kdiab.common.plugins

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm.GarbageCollectorMetricSet
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import com.codahale.metrics.jvm.ThreadStatesGaugeSet
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val NANOS_PER_SECOND: Double = 1_000_000_000.0

private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9_:]"), "_")

fun Application.configureMetrics() {
    val registry = MetricRegistry()

    install(DropwizardMetrics) {
        this.registry = registry
        registry.register("jvm.memory", MemoryUsageGaugeSet())
        registry.register("jvm.gc", GarbageCollectorMetricSet())
        registry.register("jvm.threads", ThreadStatesGaugeSet())
    }

    routing {
        get("/metrics") {
            if (call.request.headers[HttpHeaders.Authorization] == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val sb = StringBuilder()
            registry.gauges.forEach { (name, gauge) ->
                val n = sanitize(name)
                runCatching {
                    sb.appendLine("# TYPE $n gauge")
                    sb.appendLine("$n ${gauge.value}")
                }
            }
            registry.counters.forEach { (name, counter) ->
                val n = sanitize(name)
                sb.appendLine("# TYPE ${n}_total counter")
                sb.appendLine("${n}_total ${counter.count}")
            }
            registry.meters.forEach { (name, meter) ->
                val n = sanitize(name)
                sb.appendLine("# TYPE ${n}_total counter")
                sb.appendLine("${n}_total ${meter.count}")
                sb.appendLine("# TYPE ${n}_rate1m gauge")
                sb.appendLine("${n}_rate1m ${meter.oneMinuteRate}")
            }
            registry.timers.forEach { (name, timer) ->
                val n = sanitize(name)
                val snap = timer.snapshot
                sb.appendLine("# TYPE ${n}_count counter")
                sb.appendLine("${n}_count ${timer.count}")
                sb.appendLine("# TYPE ${n}_p50_seconds gauge")
                sb.appendLine("${n}_p50_seconds ${snap.median / NANOS_PER_SECOND}")
                sb.appendLine("# TYPE ${n}_p95_seconds gauge")
                sb.appendLine("${n}_p95_seconds ${snap.get95thPercentile() / NANOS_PER_SECOND}")
                sb.appendLine("# TYPE ${n}_p99_seconds gauge")
                sb.appendLine("${n}_p99_seconds ${snap.get99thPercentile() / NANOS_PER_SECOND}")
            }
            call.respondText(sb.toString(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
        }
    }
}
