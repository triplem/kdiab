package org.javafreedom.kdiab.profiles.domain.model

import kotlin.uuid.Uuid

data class Insulin(
    val id: Uuid = Uuid.random(),
    val name: String
)
