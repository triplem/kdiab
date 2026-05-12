package org.javafreedom.kdiab.carbs.plugins

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm.GarbageCollectorMetricSet
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import com.codahale.metrics.jvm.ThreadStatesGaugeSet
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
                runCatching { sb.appendLine("$name ${gauge.value}") }
            }
            registry.counters.forEach { (name, counter) ->
                sb.appendLine("$name ${counter.count}")
            }
            registry.meters.forEach { (name, meter) ->
                sb.appendLine("${name}_total ${meter.count}")
                sb.appendLine("${name}_rate1m ${meter.oneMinuteRate}")
            }
            registry.timers.forEach { (name, timer) ->
                val snap = timer.snapshot
                sb.appendLine("${name}_count ${timer.count}")
                sb.appendLine("${name}_p50 ${snap.median}")
                sb.appendLine("${name}_p95 ${snap.get95thPercentile()}")
                sb.appendLine("${name}_p99 ${snap.get99thPercentile()}")
            }
            call.respondText(sb.toString(), ContentType.Text.Plain)
        }
    }
}
