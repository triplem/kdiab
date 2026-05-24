package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import org.javafreedom.kdiab.nightscout.domain.model.Ns3SearchParams

private const val DEFAULT_LIMIT = 100

private val ALLOWED_OPERATORS = setOf("\$eq", "\$ne", "\$gt", "\$gte", "\$lt", "\$lte")

fun ApplicationCall.parseNs3SearchParams(maxLimit: Int): Ns3SearchParams =
    parseNs3SearchParams(request.queryParameters, maxLimit)

internal fun parseNs3SearchParams(params: Parameters, maxLimit: Int): Ns3SearchParams {
    val requestedLimit = params["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT
    val limit = minOf(maxLimit, maxOf(1, requestedLimit))
    val skip = maxOf(0, params["skip"]?.toIntOrNull() ?: 0)

    val sortDesc = params["sort\$desc"]
    val sortAsc = params["sort"]
    val (sortField, isDesc) = when {
        sortDesc != null -> Pair(sortDesc, true)
        sortAsc != null -> Pair(sortAsc, false)
        else -> Pair(null, false)
    }

    val fields = params["fields"]
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val filters = params.names()
        .mapNotNull { key -> parseFilterEntry(key, params[key]) }
        .groupBy({ it.first }, { it.second })

    return Ns3SearchParams(
        limit = limit,
        skip = skip,
        sortField = sortField,
        sortDesc = isDesc,
        fields = fields,
        filters = filters,
    )
}

private fun parseFilterEntry(key: String, value: String?): Pair<String, Pair<String, String>>? {
    val dollarIdx = key.lastIndexOf('$')
    val op = key.substring(dollarIdx.coerceAtLeast(0))
    return when {
        dollarIdx <= 0 || op !in ALLOWED_OPERATORS || value == null -> null
        else -> key.substring(0, dollarIdx) to Pair(op, value)
    }
}
