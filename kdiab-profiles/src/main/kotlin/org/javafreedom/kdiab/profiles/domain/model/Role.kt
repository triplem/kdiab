package org.javafreedom.kdiab.profiles.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    PATIENT,
    DOCTOR,
    ADMIN;

    companion object {
        fun fromString(value: String): Role? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}
