@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlin.time.Clock
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.domain.model.Profile as DomainProfile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus

class ProfileMapperTest {

        @Test
        fun `toDomain should map request to domain profile`() {
                val userId = Uuid.random()
                val request =
                        CreateProfileRequest(
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )

                val domain = request.toDomain(userId)

                assertEquals("Test Profile", domain.name)
                assertEquals("Fiasp", domain.insulinType)
                assertEquals(180, domain.durationOfAction)
                assertEquals(userId, domain.userId)
                assertEquals(ProfileStatus.DRAFT, domain.status)
        }

        @Test
        fun `toDomain should map request with segments to domain profile`() {
                val userId = Uuid.random()
                val request = CreateProfileRequest(
                        name = "Full Profile",
                        insulinType = "Novolog",
                        durationOfAction = 240,
                        basal = listOf(org.javafreedom.kdiab.profiles.api.models.BasalSegment("00:00", 1.5)),
                        icr = listOf(org.javafreedom.kdiab.profiles.api.models.IcrSegment("06:00", 10.0)),
                        isf = listOf(org.javafreedom.kdiab.profiles.api.models.IsfSegment("12:00", 50.0)),
                        targets = listOf(org.javafreedom.kdiab.profiles.api.models.TargetSegment("00:00", 80.0, 120.0))
                )

                val domain = request.toDomain(userId)

                assertEquals(1, domain.basal.size)
                assertEquals(1, domain.icr.size)
                assertEquals(1, domain.isf.size)
                assertEquals(1, domain.targets.size)
                assertEquals(1.5, domain.basal[0].value)
                assertEquals(10.0, domain.icr[0].value)
        }

        @Test
        fun `toApi should map domain profile to api profile`() {
                val id = Uuid.random()
                val userId = Uuid.random()
                val now = Clock.System.now()
                val domain =
                        DomainProfile(
                                id = id,
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                createdAt = now,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                val api = domain.toApi()

                assertEquals(id.toString(), api.id)
                assertEquals(userId.toString(), api.userId)
                assertEquals("Test Profile", api.name)
                assertEquals("ACTIVE", api.status.value)
        }

        @Test
        fun `toApi should include segments and previousProfileId`() {
                val id = Uuid.random()
                val previousId = Uuid.random()
                val userId = Uuid.random()
                val now = Clock.System.now()
                val domain = DomainProfile(
                        id = id,
                        userId = userId,
                        previousProfileId = previousId,
                        name = "With Segments",
                        insulinType = "Fiasp",
                        durationOfAction = 180,
                        status = ProfileStatus.ARCHIVED,
                        createdAt = now,
                        basal = listOf(org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                                kotlinx.datetime.LocalTime(0, 0), 1.2
                        )),
                        icr = listOf(org.javafreedom.kdiab.profiles.domain.model.IcrSegment(
                                kotlinx.datetime.LocalTime(6, 0), 12.0
                        )),
                        isf = listOf(org.javafreedom.kdiab.profiles.domain.model.IsfSegment(
                                kotlinx.datetime.LocalTime(12, 0), 55.0
                        )),
                        targets = listOf(org.javafreedom.kdiab.profiles.domain.model.TargetSegment(
                                kotlinx.datetime.LocalTime(0, 0), 80.0, 120.0
                        ))
                )

                val api = domain.toApi()

                assertEquals(previousId.toString(), api.previousProfileId)
                assertEquals(1, api.basal!!.size)
                assertEquals(1, api.icr!!.size)
                assertEquals(1, api.isf!!.size)
                assertEquals(1, api.targets!!.size)
                assertEquals("ARCHIVED", api.status.value)
        }

        @Test
        fun `Profile toDomain should map api model to domain`() {
                val id = Uuid.random()
                val userId = Uuid.random()
                val previousId = Uuid.random()
                val now = Clock.System.now()
                val apiProfile = org.javafreedom.kdiab.profiles.api.models.Profile(
                        id = id.toString(),
                        userId = userId.toString(),
                        name = "API Profile",
                        insulinType = "Fiasp",
                        durationOfAction = 180,
                        status = org.javafreedom.kdiab.profiles.api.models.Profile.Status.ACTIVE,
                        previousProfileId = previousId.toString(),
                        createdAt = now.toString(),
                        basal = listOf(org.javafreedom.kdiab.profiles.api.models.BasalSegment("00:00", 0.8)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList()
                )

                val domain = apiProfile.toDomain()

                assertEquals(id, domain.id)
                assertEquals(userId, domain.userId)
                assertEquals(previousId, domain.previousProfileId)
                assertEquals(ProfileStatus.ACTIVE, domain.status)
                assertEquals(1, domain.basal.size)
                assertEquals(0.8, domain.basal[0].value)
        }
}
