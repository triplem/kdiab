package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.ktor.http.ParametersBuilder
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the internal [parseNs3SearchParams] function.
 *
 * All branches of the parameter parser are covered: limit clamping, skip parsing,
 * sort ascending/descending, field projection, and filter operator parsing.
 */
class Ns3SearchParamsTest {

    private fun params(vararg pairs: Pair<String, String>): Parameters =
        ParametersBuilder().also { b -> pairs.forEach { (k, v) -> b.append(k, v) } }.build()

    // ── limit ─────────────────────────────────────────────────────────────────

    @Test
    fun `should default limit to 100 when absent`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertEquals(100, result.limit)
    }

    @Test
    fun `should use requested limit when below maxLimit`() {
        val result = parseNs3SearchParams(params("limit" to "25"), maxLimit = 1000)
        assertEquals(25, result.limit)
    }

    @Test
    fun `should cap limit to maxLimit when requested limit exceeds it`() {
        val result = parseNs3SearchParams(params("limit" to "5000"), maxLimit = 100)
        assertEquals(100, result.limit)
    }

    @Test
    fun `should enforce minimum limit of 1 when requested limit is zero`() {
        val result = parseNs3SearchParams(params("limit" to "0"), maxLimit = 1000)
        assertEquals(1, result.limit)
    }

    @Test
    fun `should enforce minimum limit of 1 when requested limit is negative`() {
        val result = parseNs3SearchParams(params("limit" to "-10"), maxLimit = 1000)
        assertEquals(1, result.limit)
    }

    @Test
    fun `should use default limit when limit is not a valid integer`() {
        val result = parseNs3SearchParams(params("limit" to "abc"), maxLimit = 1000)
        assertEquals(100, result.limit)
    }

    // ── skip ──────────────────────────────────────────────────────────────────

    @Test
    fun `should default skip to 0 when absent`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertEquals(0, result.skip)
    }

    @Test
    fun `should parse skip correctly`() {
        val result = parseNs3SearchParams(params("skip" to "50"), maxLimit = 1000)
        assertEquals(50, result.skip)
    }

    @Test
    fun `should clamp negative skip to 0`() {
        val result = parseNs3SearchParams(params("skip" to "-5"), maxLimit = 1000)
        assertEquals(0, result.skip)
    }

    // ── sort ──────────────────────────────────────────────────────────────────

    @Test
    fun `should return null sortField when no sort parameter is present`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertNull(result.sortField)
        assertEquals(false, result.sortDesc)
    }

    @Test
    fun `should parse sort ascending field`() {
        val result = parseNs3SearchParams(params("sort" to "date"), maxLimit = 1000)
        assertEquals("date", result.sortField)
        assertEquals(false, result.sortDesc)
    }

    @Test
    fun `should parse sort descending field`() {
        val result = parseNs3SearchParams(params("sort\$desc" to "date"), maxLimit = 1000)
        assertEquals("date", result.sortField)
        assertEquals(true, result.sortDesc)
    }

    @Test
    fun `should prefer sort desc over sort asc when both present`() {
        val result = parseNs3SearchParams(
            params("sort" to "identifier", "sort\$desc" to "date"),
            maxLimit = 1000,
        )
        assertEquals("date", result.sortField)
        assertEquals(true, result.sortDesc)
    }

    // ── fields ────────────────────────────────────────────────────────────────

    @Test
    fun `should return empty fields list when parameter is absent`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun `should split fields on comma`() {
        val result = parseNs3SearchParams(params("fields" to "date,sgv,direction"), maxLimit = 1000)
        assertEquals(listOf("date", "sgv", "direction"), result.fields)
    }

    @Test
    fun `should trim whitespace from field names`() {
        val result = parseNs3SearchParams(params("fields" to " date , sgv "), maxLimit = 1000)
        assertEquals(listOf("date", "sgv"), result.fields)
    }

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    fun `should parse dollar gte filter`() {
        val result = parseNs3SearchParams(params("date\$gte" to "1700000000000"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$gte", "1700000000000")), result.filters["date"])
    }

    @Test
    fun `should parse dollar lte filter`() {
        val result = parseNs3SearchParams(params("date\$lte" to "1704067200000"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$lte", "1704067200000")), result.filters["date"])
    }

    @Test
    fun `should parse dollar lt filter`() {
        val result = parseNs3SearchParams(params("date\$lt" to "1704067200000"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$lt", "1704067200000")), result.filters["date"])
    }

    @Test
    fun `should parse dollar gt filter`() {
        val result = parseNs3SearchParams(params("date\$gt" to "1700000000000"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$gt", "1700000000000")), result.filters["date"])
    }

    @Test
    fun `should parse dollar eq filter`() {
        val result = parseNs3SearchParams(params("type\$eq" to "sgv"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$eq", "sgv")), result.filters["type"])
    }

    @Test
    fun `should parse dollar ne filter`() {
        val result = parseNs3SearchParams(params("type\$ne" to "mbg"), maxLimit = 1000)
        assertEquals(listOf(Pair("\$ne", "mbg")), result.filters["type"])
    }

    @Test
    fun `should collect both gte and lte for date range`() {
        val result = parseNs3SearchParams(
            params("date\$gte" to "1700000000000", "date\$lte" to "1704067200000"),
            maxLimit = 1000,
        )
        val dateFilters = result.filters["date"] ?: emptyList()
        assertEquals(2, dateFilters.size)
        assertTrue(dateFilters.any { (op, v) -> op == "\$gte" && v == "1700000000000" })
        assertTrue(dateFilters.any { (op, v) -> op == "\$lte" && v == "1704067200000" })
    }

    @Test
    fun `should ignore unknown operator dollar regex`() {
        val result = parseNs3SearchParams(params("date\$regex" to "foo"), maxLimit = 1000)
        assertTrue(result.filters.isEmpty())
    }

    @Test
    fun `should return empty filters when no filter parameters are present`() {
        val result = parseNs3SearchParams(params("limit" to "10"), maxLimit = 1000)
        assertTrue(result.filters.isEmpty())
    }
}
