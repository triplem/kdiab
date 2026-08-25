package org.javafreedom.kdiab.profiles.application.api

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.profiles.domain.model.Insulin
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.profiles.domain.repository.InsulinRepository
import org.javafreedom.kdiab.profiles.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
private fun Application.installMockDi(
    profileService: ProfileService,
    insulinService: InsulinService,
    auditLogRepository: AuditLogRepository,
) {
    install(DI) { }
    dependencies {
        provide<ProfileService> { profileService }
        provide<InsulinService> { insulinService }
        provide<AuditLogRepository> { auditLogRepository }
    }
}

class InsulinApiTest {

    private fun generateToken(role: Role, userId: Uuid = Uuid.random()): String =
        SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder()
                .audience("profile")
                .issuer("org.javafreedom.kdiab")
                .subject(userId.toString())
                .claim("roles", listOf(role.name))
                .build()
        ).apply { sign(MACSigner("secret-hs256-min32-bytes-0123456789ab".toByteArray())) }.serialize()

    private fun ApplicationTestBuilder.setupApp(repo: InsulinRepository) {
        environment {
            config = MapApplicationConfig(
                "jwt.audience"     to "profile",
                "jwt.domain"       to "org.javafreedom.kdiab",
                "jwt.realm"        to "kdiab-profiles",
                "jwt.secret"       to "secret-hs256-min32-bytes-0123456789ab",
                "jwt.test"         to "true",
                "jwt.allowTestMode" to "true",
                "app.initDatabase" to "false",
            )
        }
        application {
            installMockDi(
                mockk(relaxed = true),
                InsulinService(repo),
                mockk(relaxed = true),
            )
            module()
        }
    }

    // ── POST: non-admin → 403 ────────────────────────────────────────────────
    @Test
    fun `POST insulin returns 403 for a PATIENT`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.PATIENT)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fiasp"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { repo.create(any()) }
    }

    @Test
    fun `POST insulin returns 403 for a DOCTOR`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.DOCTOR)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fiasp"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { repo.create(any()) }
    }

    // ── POST: ADMIN validation errors ────────────────────────────────────────
    @Test
    fun `POST insulin returns 400 for blank name`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { repo.create(any()) }
    }

    // ── POST: duplicate name → 409 ───────────────────────────────────────────
    // The route wraps ExposedSQLException in ConflictException; we verify the 409 mapping
    // by injecting a ConflictException directly (avoids StackOverflow from the relaxed mock
    // during logger serialisation, since ExposedSQLException holds live DB state).
    @Test
    fun `POST insulin returns 409 when a ConflictException is raised`() = testApplication {
        val repo = mockk<InsulinRepository>()
        coEvery { repo.create("Fiasp") } throws
            org.javafreedom.kdiab.common.domain.exception.ConflictException(
                "An insulin with that name already exists"
            )
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fiasp"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ── PUT: blank name → 400 ────────────────────────────────────────────────
    @Test
    fun `PUT insulin returns 400 for blank name`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val id = Uuid.random()
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { repo.update(any(), any()) }
    }

    // ── PUT: duplicate name → 409 ────────────────────────────────────────────────
    @Test
    fun `PUT insulin returns 409 when a ConflictException is raised`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val id = Uuid.random()
        coEvery { repo.update(id, "Fiasp") } throws
            org.javafreedom.kdiab.common.domain.exception.ConflictException(
                "An insulin with that name already exists"
            )
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fiasp"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ── GET: any authenticated user can list insulins ──────────────────────────

    @Test
    fun `GET insulins returns 200 for a PATIENT`() = testApplication {
        val repo = mockk<InsulinRepository>()
        coEvery { repo.findAll() } returns emptyList()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.PATIENT)}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── POST: ADMIN can create insulins ──────────────────────────────────────

    @Test
    fun `POST insulins returns 201 for an ADMIN`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val insulinId = Uuid.random()
        coEvery { repo.create("Fiasp") } returns Insulin(id = insulinId, name = "Fiasp")
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/api/v1/insulins") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Fiasp"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    // ── PUT: non-admin → 403 ──────────────────────────────────────────────────

    @Test
    fun `PUT insulin returns 403 for a PATIENT`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val id = Uuid.random()
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.PATIENT)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { repo.update(any(), any()) }
    }

    @Test
    fun `PUT insulin returns 403 for a DOCTOR`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val id = Uuid.random()
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.DOCTOR)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── PUT: admin → delegates to repository ─────────────────────────────────

    @Test
    fun `PUT insulin returns 200 for ADMIN when insulin exists`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val id = Uuid.random()
        coEvery { repo.update(id, "Updated") } returns Insulin(id = id, name = "Updated")
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { repo.update(id, "Updated") }
    }

    @Test
    fun `PUT insulin returns 404 for ADMIN when insulin does not exist`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val id = Uuid.random()
        coEvery { repo.update(id, "Updated") } returns null
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.put("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Updated"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── DELETE: non-admin → 403 ───────────────────────────────────────────────

    @Test
    fun `DELETE insulin returns 403 for a PATIENT`() = testApplication {
        val repo = mockk<InsulinRepository>()
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val id = Uuid.random()
        val response = client.delete("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.PATIENT)}")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    // ── DELETE: admin → delegates to repository ───────────────────────────────

    @Test
    fun `DELETE insulin returns 204 for ADMIN when insulin exists`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val id = Uuid.random()
        coEvery { repo.delete(id) } returns true
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.delete("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { repo.delete(id) }
    }

    @Test
    fun `DELETE insulin returns 404 for ADMIN when insulin does not exist`() = testApplication {
        val repo = mockk<InsulinRepository>()
        val id = Uuid.random()
        coEvery { repo.delete(id) } returns false
        setupApp(repo)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.delete("/api/v1/insulins/$id") {
            header(HttpHeaders.Authorization, "Bearer ${generateToken(Role.ADMIN)}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
