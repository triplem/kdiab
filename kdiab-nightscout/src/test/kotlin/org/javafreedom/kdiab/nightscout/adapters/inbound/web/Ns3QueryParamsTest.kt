package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ns3QueryParamsTest {

    private fun params(vararg pairs: Pair<String, String>): Parameters =
        ParametersBuilder().also { b -> pairs.forEach { (k, v) -> b.append(k, v) } }.build()

    @Test
    fun `limit is capped at maxLimit`() {
        val result = parseNs3SearchParams(params("limit" to "5000"), maxLimit = 100)
        assertEquals(100, result.limit)
    }

    @Test
    fun `limit defaults to 100 when absent`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertEquals(100, result.limit)
    }

    @Test
    fun `limit uses requested value when below maxLimit`() {
        val result = parseNs3SearchParams(params("limit" to "50"), maxLimit = 1000)
        assertEquals(50, result.limit)
    }

    @Test
    fun `skip defaults to 0`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertEquals(0, result.skip)
    }

    @Test
    fun `skip is parsed correctly`() {
        val result = parseNs3SearchParams(params("skip" to "20"), maxLimit = 1000)
        assertEquals(20, result.skip)
    }

    @Test
    fun `sort asc field is parsed`() {
        val result = parseNs3SearchParams(params("sort" to "date"), maxLimit = 1000)
        assertEquals("date", result.sortField)
        assertEquals(false, result.sortDesc)
    }

    @Test
    fun `sort desc field is parsed`() {
        val result = parseNs3SearchParams(params("sort\$desc" to "date"), maxLimit = 1000)
        assertEquals("date", result.sortField)
        assertEquals(true, result.sortDesc)
    }

    @Test
    fun `no sort field returns null`() {
        val result = parseNs3SearchParams(params(), maxLimit = 1000)
        assertNull(result.sortField)
    }

    @Test
    fun `fields are split on comma`() {
        val result = parseNs3SearchParams(params("fields" to "date,sgv,direction"), maxLimit = 1000)
        assertEquals(listOf("date", "sgv", "direction"), result.fields)
    }

    @Test
    fun `date dollar gte filter is parsed correctly`() {
        val result = parseNs3SearchParams(params("date\$gte" to "1700000000000"), maxLimit = 1000)
        assertEquals(Pair("\$gte", "1700000000000"), result.filters["date"])
    }

    @Test
    fun `date dollar lt filter is parsed correctly`() {
        val result = parseNs3SearchParams(params("date\$lt" to "1704067200000"), maxLimit = 1000)
        assertEquals(Pair("\$lt", "1704067200000"), result.filters["date"])
    }

    @Test
    fun `unknown operator is ignored`() {
        val result = parseNs3SearchParams(params("date\$regex" to "foo"), maxLimit = 1000)
        assertTrue(result.filters.isEmpty())
    }

    @Test
    fun `API3_MAX_LIMIT env caps limit to configured value`() {
        val result = parseNs3SearchParams(params("limit" to "999"), maxLimit = 50)
        assertEquals(50, result.limit)
    }
}
