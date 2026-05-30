@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlin.time.Clock
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.api.models.InsulinSettings as ApiInsulinSettings
import org.javafreedom.kdiab.profiles.api.models.ProfileSchedule as ApiProfileSchedule
import org.javafreedom.kdiab.profiles.domain.model.AnalysisRange
import org.javafreedom.kdiab.profiles.domain.model.InsulinSettings
import org.javafreedom.kdiab.profiles.domain.model.ProfileCollaboration
import org.javafreedom.kdiab.profiles.domain.model.ProfileSchedule
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.model.Profile as DomainProfile

class ProfileMapperTest {

        @Test
        fun `toDomain should map request with legacy flat fields to domain profile`() {
                val userId = Uuid.random()
                val request = CreateProfileRequest(
                        name = "Test Profile",
                        insulinType = "Fiasp",
                        durationOfAction = 180
                )

                val domain = request.toDomain(userId)

                assertEquals("Test Profile", domain.name)
                assertEquals("Fiasp", domain.settings.insulinType)
                assertEquals(180, domain.settings.durationOfAction)
                assertEquals(userId, domain.userId)
                assertEquals(ProfileStatus.DRAFT, domain.status)
        }

        @Test
        fun `toDomain should map request with new nested settings to domain profile`() {
                val userId = Uuid.random()
                val request = CreateProfileRequest(
                        name = "Test Profile",
                        settings = ApiInsulinSettings(insulinType = "Novolog", durationOfAction = 240)
                )

                val domain = request.toDomain(userId)

                assertEquals("Novolog", domain.settings.insulinType)
                assertEquals(240, domain.settings.durationOfAction)
        }

        @Test
        fun `toDomain should prefer nested settings over legacy flat fields`() {
                val userId = Uuid.random()
                val request = CreateProfileRequest(
                        name = "Test",
                        // Both provided — new nested takes precedence
                        settings = ApiInsulinSettings(insulinType = "Novolog", durationOfAction = 240),
                        insulinType = "Fiasp",
                        durationOfAction = 180
                )

                val domain = request.toDomain(userId)

                assertEquals("Novolog", domain.settings.insulinType)
                assertEquals(240, domain.settings.durationOfAction)
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

                assertEquals(1, domain.schedule.basal.size)
                assertEquals(1, domain.schedule.icr.size)
                assertEquals(1, domain.schedule.isf.size)
                assertEquals(1, domain.schedule.targets.size)
                assertEquals(1.5, domain.schedule.basal[0].value)
                assertEquals(10.0, domain.schedule.icr[0].value)
        }

        @Test
        fun `toDomain should map request with new nested schedule to domain profile`() {
                val userId = Uuid.random()
                val request = CreateProfileRequest(
                        name = "Full Profile",
                        insulinType = "Novolog",
                        durationOfAction = 240,
                        schedule = ApiProfileSchedule(
                            basal = listOf(org.javafreedom.kdiab.profiles.api.models.BasalSegment("00:00", 2.0)),
                            icr = listOf(org.javafreedom.kdiab.profiles.api.models.IcrSegment("00:00", 12.0)),
                            isf = listOf(org.javafreedom.kdiab.profiles.api.models.IsfSegment("00:00", 45.0)),
                            targets = listOf(org.javafreedom.kdiab.profiles.api.models.TargetSegment("00:00", 90.0, 130.0))
                        )
                )

                val domain = request.toDomain(userId)

                assertEquals(1, domain.schedule.basal.size)
                assertEquals(2.0, domain.schedule.basal[0].value)
                assertEquals(12.0, domain.schedule.icr[0].value)
        }

        @Test
        fun `toApi should map domain profile to api profile`() {
                val id = Uuid.random()
                val userId = Uuid.random()
                val now = Clock.System.now()
                val domain = DomainProfile(
                        id = id,
                        userId = userId,
                        name = "Test Profile",
                        status = ProfileStatus.ACTIVE,
                        createdAt = now,
                        settings = InsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        schedule = ProfileSchedule(
                            basal = emptyList(), icr = emptyList(),
                            isf = emptyList(), targets = emptyList()
                        )
                )

                val api = domain.toApi()

                assertEquals(id.toString(), api.id)
                assertEquals(userId.toString(), api.userId)
                assertEquals("Test Profile", api.name)
                assertEquals("ACTIVE", api.status.value)
                // New nested fields
                assertNotNull(api.settings)
                assertEquals("Fiasp", api.settings.insulinType)
                assertEquals(180, api.settings.durationOfAction)
                // Legacy flat fields still populated for backward compat
                assertEquals("Fiasp", api.insulinType)
                assertEquals(180, api.durationOfAction)
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
                        status = ProfileStatus.ARCHIVED,
                        createdAt = now,
                        settings = InsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        schedule = ProfileSchedule(
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
                )

                val api = domain.toApi()

                assertEquals(previousId.toString(), api.previousProfileId)
                assertEquals(1, api.schedule.basal.size)
                assertEquals(1, api.schedule.icr.size)
                assertEquals(1, api.schedule.isf.size)
                assertEquals(1, api.schedule.targets.size)
                // Legacy flat fields also populated
                assertEquals(1, api.basal!!.size)
                assertEquals("ARCHIVED", api.status.value)
        }

        @Test
        fun `toApi should map analysisRange to both new and legacy fields`() {
                val domain = DomainProfile(
                        id = Uuid.random(), userId = Uuid.random(),
                        name = "Test", status = ProfileStatus.DRAFT, createdAt = Clock.System.now(),
                        settings = InsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        analysisRange = AnalysisRange(low = 70.0, high = 180.0),
                        schedule = ProfileSchedule(emptyList(), emptyList(), emptyList(), emptyList())
                )

                val api = domain.toApi()

                assertNotNull(api.analysisRange)
                assertEquals(70.0, api.analysisRange!!.low)
                assertEquals(180.0, api.analysisRange!!.high)
                // Legacy flat fields
                assertEquals(70.0, api.analysisLow)
                assertEquals(180.0, api.analysisHigh)
        }

        @Test
        fun `toApi should map collaboration to both new and legacy fields`() {
                val domain = DomainProfile(
                        id = Uuid.random(), userId = Uuid.random(),
                        name = "Test", status = ProfileStatus.PROPOSED, createdAt = Clock.System.now(),
                        settings = InsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        schedule = ProfileSchedule(emptyList(), emptyList(), emptyList(), emptyList()),
                        collaboration = ProfileCollaboration(proposalReason = "Doctor suggested change")
                )

                val api = domain.toApi()

                assertNotNull(api.collaboration)
                assertEquals("Doctor suggested change", api.collaboration!!.proposalReason)
                // Legacy flat field
                assertEquals("Doctor suggested change", api.proposalReason)
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
                        status = org.javafreedom.kdiab.profiles.api.models.Profile.Status.ACTIVE,
                        settings = ApiInsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        schedule = ApiProfileSchedule(
                            basal = listOf(org.javafreedom.kdiab.profiles.api.models.BasalSegment("00:00", 0.8)),
                            icr = emptyList(), isf = emptyList(), targets = emptyList()
                        ),
                        previousProfileId = previousId.toString(),
                        createdAt = now.toString()
                )

                val domain = apiProfile.toDomain()

                assertEquals(id, domain.id)
                assertEquals(userId, domain.userId)
                assertEquals(previousId, domain.previousProfileId)
                assertEquals(ProfileStatus.ACTIVE, domain.status)
                assertEquals("Fiasp", domain.settings.insulinType)
                assertEquals(180, domain.settings.durationOfAction)
                assertEquals(1, domain.schedule.basal.size)
                assertEquals(0.8, domain.schedule.basal[0].value)
        }

        @Test
        fun `Profile toDomain should prefer nested settings over legacy flat fields in api model`() {
                val id = Uuid.random()
                val userId = Uuid.random()
                // Provide both nested (takes precedence) and legacy flat fields
                val apiProfile = org.javafreedom.kdiab.profiles.api.models.Profile(
                        id = id.toString(),
                        userId = userId.toString(),
                        name = "Legacy Profile",
                        status = org.javafreedom.kdiab.profiles.api.models.Profile.Status.DRAFT,
                        settings = ApiInsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
                        schedule = ApiProfileSchedule(
                            basal = listOf(org.javafreedom.kdiab.profiles.api.models.BasalSegment("00:00", 1.0)),
                            icr = emptyList(), isf = emptyList(), targets = emptyList()
                        ),
                        // Legacy flat fields — nested takes precedence
                        insulinType = "OldInsulin",
                        durationOfAction = 999
                )

                val domain = apiProfile.toDomain()

                // Nested settings win over legacy flat fields
                assertEquals("Fiasp", domain.settings.insulinType)
                assertEquals(180, domain.settings.durationOfAction)
                assertEquals(1, domain.schedule.basal.size)
        }
}
