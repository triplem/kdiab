package org.javafreedom.kdiab.profiles.util

import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SerializerTest {

    @Serializable
    data class TestData(
            @Serializable(with = InstantSerializer::class) val instant: Instant,
            @Serializable(with = LocalTimeSerializer::class) val time: LocalTime,
            @Serializable(with = UUIDSerializer::class) val uuid: UUID
    )

    private val json = Json { prettyPrint = true }

    @Test
    fun `should serialize and deserialize using custom serializers`() {
        val instant = Instant.parse("2023-10-01T12:00:00Z")
        val time = LocalTime.of(12, 30)
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val data = TestData(instant, time, uuid)
        val jsonString = json.encodeToString(TestData.serializer(), data)

        val decoded = json.decodeFromString(TestData.serializer(), jsonString)

        assertEquals(instant, decoded.instant)
        assertEquals(time, decoded.time)
        assertEquals(uuid, decoded.uuid)
    }
}
