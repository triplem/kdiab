package org.javafreedom.kdiab.bff.domain.model

enum class Role {
    PATIENT, DOCTOR, ADMIN;

    companion object {
        fun fromString(role: String): Role? = entries.find { it.name.equals(role, ignoreCase = true) }
    }
}
