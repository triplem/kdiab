package org.javafreedom.kdiab.nightscout.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Ns3Response<T>(
    val status: Int,
    val result: T? = null,
    val identifier: String? = null,
)

@Serializable
data class Ns3ListResponse<T>(
    val status: Int,
    val result: List<T>,
)

@Serializable
data class Ns3Entry(
    val identifier: String,
    val date: Long,
    val dateString: String,
    val utcOffset: Int = 0,
    val type: String,
    val sgv: Double? = null,
    val direction: String? = null,
    val noise: Int? = null,
    val device: String = "kdiab",
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3Treatment(
    val identifier: String,
    val date: Long,
    val dateString: String,
    val utcOffset: Int = 0,
    val eventType: String,
    val insulin: Double? = null,
    val carbs: Double? = null,
    val absorptionTime: Int? = null,
    val duration: Int? = null,
    val percent: Double? = null,
    val rate: Double? = null,
    val absolute: Boolean? = null,
    val notes: String? = null,
    val enteredBy: String = "kdiab",
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3Food(
    val identifier: String,
    val name: String,
    val carbs: Double,
    val fat: Double? = null,
    val protein: Double? = null,
    val energy: Double? = null,
    val unit: String = "g",
    val portionSize: Double? = null,
    val portionUnit: String? = null,
    val category: String? = null,
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3BasalSegment(
    val time: String,
    val value: Double,
)

@Serializable
data class Ns3Profile(
    val identifier: String,
    val defaultProfile: String,
    val startDate: String,
    val units: String,
    val timeZone: String = "UTC",
    val dia: Double,
    val basalSegments: List<Ns3BasalSegment>,
    val carbratio: List<Map<String, Double>>,
    val sens: List<Map<String, Double>>,
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3Settings(
    val identifier: String,
    val units: String,
    val timeZone: String = "UTC",
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3DeviceStatus(
    val identifier: String,
    val date: Long,
    val dateString: String,
    val device: String? = null,
    val uploaderBattery: Int? = null,
    val pump: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val openaps: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val loop: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val srvCreated: Long? = null,
    val srvModified: Long? = null,
)

@Serializable
data class Ns3VersionResult(
    val version: String = "15.0.0",
    val apiVersion: String = "v3",
    val srvDate: Long,
)

@Serializable
data class Ns3StatusResult(
    val isAuthenticated: Boolean,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class Ns3LastModifiedResult(
    val srvDate: Long,
    val collections: Map<String, Long?>,
)
