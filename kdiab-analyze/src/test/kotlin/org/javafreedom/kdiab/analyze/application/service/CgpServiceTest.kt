package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CgpServiceTest {

    private val measuresPort = mockk<MeasuresPort>()
    private val profilesPort = mockk<ProfilesPort>()
    private val treatmentsPort = mockk<TreatmentsPort>()
    private val service = AnalyticsService(measuresPort, profilesPort, treatmentsPort)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val correlationId = "corr-1"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-15T00:00:00Z"   // 14 days

    private fun cgmDto(sgv: Double, unit: String = "mg/dL", ts: String = "2024-01-08T12:00:00Z") =
        UpstreamMeasure(
            id = "m-${sgv.toLong()}",
            userId = userId,
            measuredAt = ts,
            type = "CGM",
            source = "SENSOR",
            data = buildJsonObject { put("value", sgv); put("unit", unit) },
            status = "ACTIVE",
        )

    // Returns enough 100 mg/dL readings to have ≥14 days (4032 readings)
    private fun perfectReadings(count: Int = 4032): List<UpstreamMeasure> =
        (0 until count).map { i ->
            val ts = "2024-01-01T%02d:%02d:00Z".format((i * 5 / 60) % 24, (i * 5) % 60)
            cgmDto(100.0, ts = ts)
        }

    // ── Reference values ────────────────────────────────────────────────────

    @Test
    fun `refTor is 0 and refMeanGlucose is 100`() = runTest {
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns perfectReadings()
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.refTor)
        assertEquals(100.0, result.refMeanGlucose)
        assertEquals(12.5, result.refVarK)
        assertEquals(0.0, result.refHypo)
        assertEquals(0.0, result.refHyper)
    }

    // ── ToR ─────────────────────────────────────────────────────────────────

    @Test
    fun `tor is zero when all readings are in range`() = runTest {
        val readings = (0 until 4032).map { cgmDto(120.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.tor)
    }

    @Test
    fun `tor is positive when some readings are out of range`() = runTest {
        val inRange = (0 until 3000).map { cgmDto(120.0) }
        val outOfRange = (0 until 1000).map { cgmDto(50.0) }   // hypo
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns inRange + outOfRange
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.tor > 0.0, "Expected tor > 0 but got ${result.tor}")
    }

    @Test
    fun `normTor is between 0 and 1`() = runTest {
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns perfectReadings()
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.normTor in 0.0..1.0, "normTor=${result.normTor} out of range")
    }

    // ── VarK ────────────────────────────────────────────────────────────────

    @Test
    fun `varK is zero when all readings are identical`() = runTest {
        val readings = (0 until 4032).map { cgmDto(120.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.varK)
    }

    @Test
    fun `varK is positive when readings vary`() = runTest {
        val low = (0 until 2000).map { cgmDto(80.0) }
        val high = (0 until 2000).map { cgmDto(200.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns low + high
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.varK > 0.0, "Expected varK > 0 but got ${result.varK}")
    }

    @Test
    fun `normVarK is between 0 and 1`() = runTest {
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns perfectReadings()
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.normVarK in 0.0..1.0, "normVarK=${result.normVarK} out of range")
    }

    // ── Hypo / hyper intensity ───────────────────────────────────────────────

    @Test
    fun `hypoIntensity is zero when no readings below 70`() = runTest {
        val readings = (0 until 4032).map { cgmDto(100.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.hypoIntensity)
    }

    @Test
    fun `hyperIntensity is zero when no readings above 180`() = runTest {
        val readings = (0 until 4032).map { cgmDto(150.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.hyperIntensity)
    }

    @Test
    fun `hypoIntensity is positive when hypo readings present`() = runTest {
        val inRange = (0 until 3000).map { cgmDto(100.0) }
        val hypo = (0 until 1000).map { cgmDto(50.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns inRange + hypo
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.hypoIntensity > 0.0)
    }

    @Test
    fun `hyperIntensity is positive when hyper readings present`() = runTest {
        val inRange = (0 until 3000).map { cgmDto(100.0) }
        val hyper = (0 until 1000).map { cgmDto(250.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns inRange + hyper
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.hyperIntensity > 0.0)
    }

    // ── Mean glucose ─────────────────────────────────────────────────────────

    @Test
    fun `meanGlucose is average of all readings`() = runTest {
        val readings = listOf(cgmDto(100.0), cgmDto(200.0))
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(150.0, result.meanGlucose, 0.001)
    }

    @Test
    fun `normMeanGlucose is 1 when mean equals 100`() = runTest {
        val readings = (0 until 4032).map { cgmDto(100.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(1.0, result.normMeanGlucose, 0.001)
    }

    @Test
    fun `normMeanGlucose is 0 when mean equals 300`() = runTest {
        val readings = (0 until 4032).map { cgmDto(300.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.normMeanGlucose, 0.001)
    }

    // ── PGR score ────────────────────────────────────────────────────────────

    @Test
    fun `pgr is 5 for perfect data at reference values`() = runTest {
        // All readings at exactly 100 mg/dL with zero variance and zero out-of-range
        val readings = (0 until 4032).map { cgmDto(100.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(5.0, result.pgr, 0.001)
    }

    @Test
    fun `pgr is between 0 and 5`() = runTest {
        val readings = (0 until 2000).map { cgmDto(50.0) } + (0 until 2000).map { cgmDto(300.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.pgr in 0.0..5.0, "pgr=${result.pgr} out of [0,5]")
    }

    // ── PGR risk categories ──────────────────────────────────────────────────

    @Test
    fun `pgrRisk is very_high for pgr 5`() = runTest {
        val readings = (0 until 4032).map { cgmDto(100.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals("very_high", result.pgrRisk)
    }

    @Test
    fun `pgrRisk is very_low for very poor readings`() = runTest {
        val readings = (0 until 4032).map { cgmDto(20.0) }  // severe hypo → worst case
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals("very_low", result.pgrRisk)
    }

    // ── Non-CGM readings are ignored ─────────────────────────────────────────

    @Test
    fun `non-CGM readings are excluded from computation`() = runTest {
        val cgmReadings = (0 until 4032).map { cgmDto(100.0) }
        val bgmReading = UpstreamMeasure(
            id = "bgm-1",
            userId = userId,
            measuredAt = "2024-01-08T12:00:00Z",
            type = "BGM",
            source = "MANUAL",
            data = buildJsonObject { put("value", 50.0); put("unit", "mg/dL") },
            status = "ACTIVE",
        )
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns cgmReadings + listOf(bgmReading)
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        // BGM reading of 50 would lower meanGlucose and add hypoIntensity if included
        assertEquals(100.0, result.meanGlucose, 0.001)
        assertEquals(0.0, result.hypoIntensity, 0.001)
    }

    // ── mmol/L conversion ────────────────────────────────────────────────────

    @Test
    fun `mmol L readings are converted to mg dL before computation`() = runTest {
        // 5.55 mmol/L × 18.0182 ≈ 100.0 mg/dL
        val readings = (0 until 4032).map { cgmDto(5.55, unit = "mmol/L") }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mmol/L", correlationId)
        // mean should be approximately 100 mg/dL
        assertTrue(result.meanGlucose in 99.0..101.0, "Expected ~100 mg/dL but got ${result.meanGlucose}")
    }

    // ── Upstream error handling ──────────────────────────────────────────────

    @Test
    fun `upstream error returns empty result with warning`() = runTest {
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable", null, null, null)
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertEquals(0.0, result.pgr)
        assertTrue(result.warnings.isNotEmpty())
    }

    // ── Warnings ─────────────────────────────────────────────────────────────

    @Test
    fun `warning is issued when no CGM readings`() = runTest {
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.warnings.any { it.contains("No CGM readings") })
    }

    @Test
    fun `warning is issued when fewer than 14 days of data`() = runTest {
        // Only 100 readings (well below 4032)
        val readings = (0 until 100).map { cgmDto(100.0) }
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.warnings.any { it.contains("14 days") })
    }

    @Test
    fun `no warning issued when exactly 14 days of data`() = runTest {
        val readings = perfectReadings(4032)
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns readings
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        assertTrue(result.warnings.none { it.contains("14 days") })
    }

    // ── Zero and negative readings excluded ──────────────────────────────────

    @Test
    fun `readings with zero or negative values are excluded`() = runTest {
        val valid = (0 until 4032).map { cgmDto(100.0) }
        val invalid = listOf(cgmDto(0.0), cgmDto(-5.0))
        coEvery { measuresPort.getMeasures(any(), any(), any(), any(), any()) } returns valid + invalid
        val result = service.getCgp(userId, from, to, auth, "mg/dL", correlationId)
        // mean should remain 100 mg/dL if invalid readings are excluded
        assertEquals(100.0, result.meanGlucose, 0.001)
    }
}
