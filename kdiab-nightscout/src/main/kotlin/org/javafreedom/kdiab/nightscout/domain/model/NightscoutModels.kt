package org.javafreedom.kdiab.nightscout.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NightscoutEntry(
    val type: String,
    val sgv: Int? = null,
    val date: Long,
    val dateString: String,
    val trend: Int? = null,
    val direction: String? = null,
    @SerialName("_id") val id: String,
    val mills: Long,
    val noise: Int = 0,
    val device: String = "kdiab",
)

@Serializable
data class NightscoutTreatment(
    @SerialName("_id") val id: String,
    val eventType: String,
    @SerialName("created_at") val createdAt: String,
    val timestamp: String? = null,
    val insulin: Double? = null,
    val carbs: Double? = null,
    val absorptionTime: Int? = null,
    val notes: String? = null,
    val enteredBy: String = "kdiab",
    val utcOffset: Int = 0,
    val mills: Long,
)

@Serializable
data class NightscoutStatus(
    val status: String,
    val apiEnabled: Boolean,
    val serverTime: String,
    val serverTimeEpoch: Long,
    val version: String = "15.0.0",
)
