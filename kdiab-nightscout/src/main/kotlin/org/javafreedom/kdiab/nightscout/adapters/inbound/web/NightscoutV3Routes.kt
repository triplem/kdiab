@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.UserSettingsClient
import org.javafreedom.kdiab.nightscout.application.service.NightscoutV3Service
import org.javafreedom.kdiab.nightscout.domain.model.Ns3DeviceStatus
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3HistoryResult
import org.javafreedom.kdiab.nightscout.domain.model.Ns3LastModifiedResult
import org.javafreedom.kdiab.nightscout.domain.model.Ns3ListResponse
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Response
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Settings
import org.javafreedom.kdiab.nightscout.domain.model.Ns3StatusResult
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment
import org.javafreedom.kdiab.nightscout.domain.model.Ns3VersionResult

private val SERVER_STARTED_MS = System.currentTimeMillis()

fun Route.nightscoutV3Routes(service: NightscoutV3Service, maxLimit: Int, userSettingsClient: UserSettingsClient) {
    // NS3 protocol requires version and status to be publicly accessible before auth
    get("/api/v3/version") {
        call.respond(
            Ns3Response(
                status = 200,
                result = Ns3VersionResult(
                    srvDate = System.currentTimeMillis(),
                    lastModified = SERVER_STARTED_MS,
                ),
            )
        )
    }

    get("/api/v3/status") {
        call.respond(
            Ns3Response(
                status = 200,
                result = Ns3StatusResult(
                    isAuthenticated = true,
                    permissions = listOf("*:*", "api:read", "api:create", "api:update", "api:delete"),
                    lastModified = SERVER_STARTED_MS,
                ),
            )
        )
    }

    authenticate("auth-jwt") {
        get("/api/v3/lastModified") {
            val now = System.currentTimeMillis()
            call.respond(
                Ns3Response(
                    status = 200,
                    result = Ns3LastModifiedResult(
                        srvDate = now,
                        collections = mapOf(
                            "entries" to now,
                            "treatments" to now,
                            "foods" to now,
                            "profile" to now,
                            "devicestatus" to now,
                        ),
                    ),
                )
            )
        }

        nightscoutV3EntriesRoutes(service, maxLimit, userSettingsClient)

        // TODO(#894-#898): stub HISTORY endpoints for collections implemented in parallel PRs
        for (collection in listOf("treatments", "foods", "profile", "devicestatus")) {
            route("/api/v3/$collection/history") {
                get {
                    call.respond(
                        Ns3HistoryResult<String>(
                            status = 200,
                            result = emptyList(),
                            lastModified = System.currentTimeMillis(),
                        )
                    )
                }
                get("/{lastModified}") {
                    call.respond(
                        Ns3HistoryResult<String>(
                            status = 200,
                            result = emptyList(),
                            lastModified = call.parameters["lastModified"]?.toLongOrNull()
                                ?: System.currentTimeMillis(),
                        )
                    )
                }
            }
        }

        nightscoutV3TreatmentsRoutes(service, maxLimit)
        nightscoutV3FoodRoutes(service, maxLimit)
        settingsRoutes(service, userSettingsClient)
        nightscoutV3ProfileRoutes(service, maxLimit)
        nightscoutV3DeviceStatusRoutes(service, maxLimit)
    }
}

@Suppress("LongMethod")
private fun Route.nightscoutV3ProfileRoutes(service: NightscoutV3Service, maxLimit: Int) {
    route("/api/v3/profile") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val params = call.parseNs3SearchParams(maxLimit)
            val profiles = service.searchProfiles(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                params = params,
            )
            call.respond(Ns3ListResponse(status = 200, result = profiles))
        }
        post {
            val principal = call.principal<UserPrincipal>()!!
            val profile = call.receive<Ns3Profile>()
            val created = service.createProfile(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                profile = profile,
            )
            call.response.header("Location", "/api/v3/profile/${created.identifier}")
            val createResp = Ns3Response<Ns3Profile>(status = 201, identifier = created.identifier)
            call.respond(HttpStatusCode.Created, createResp)
        }
        get("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val profile = service.getProfile(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
            )
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Profile>(status = 404))
            } else {
                call.respond(Ns3Response(status = 200, result = profile))
            }
        }
        put("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val profile = call.receive<Ns3Profile>()
            val updated = service.updateProfile(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                profile = profile,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        delete("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val permanent = call.request.queryParameters["permanent"] == "true"
            if (permanent) {
                val resp = ErrorResponse(
                    HttpStatusCode.BadRequest.value,
                    "Permanent deletion is not supported for profiles",
                )
                call.respond(HttpStatusCode.BadRequest, resp)
                return@delete
            }
            service.deleteProfile(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                permanent = false,
            )
            call.respond(Ns3Response<Unit>(status = 200))
        }
    }
}

private fun Route.settingsRoutes(service: NightscoutV3Service, userSettingsClient: UserSettingsClient) {
    route("/api/v3/settings") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = authorization,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
        put {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val body = call.receive<Ns3Settings>()
            val glucoseUnit = if (body.units.isNotEmpty()) {
                val fetched = userSettingsClient.getGlucoseUnit(authorization)
                if (body.units != fetched) {
                    call.respond(HttpStatusCode.UnprocessableEntity, Ns3Response<Ns3Settings>(status = 422))
                    return@put
                }
                fetched
            } else null
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = authorization,
                glucoseUnit = glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
        patch {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val body = call.receive<Ns3Settings>()
            val glucoseUnit = if (body.units.isNotEmpty()) {
                val fetched = userSettingsClient.getGlucoseUnit(authorization)
                if (body.units != fetched) {
                    call.respond(HttpStatusCode.UnprocessableEntity, Ns3Response<Ns3Settings>(status = 422))
                    return@patch
                }
                fetched
            } else null
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = authorization,
                glucoseUnit = glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
    }
}

private fun Route.nightscoutV3DeviceStatusRoutes(service: NightscoutV3Service, maxLimit: Int) {
    route("/api/v3/devicestatus") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val params = call.parseNs3SearchParams(maxLimit)
            val result = service.searchDeviceStatus(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                params = params,
            )
            call.respond(Ns3ListResponse(status = 200, result = result))
        }
        post {
            val principal = call.principal<UserPrincipal>()!!
            val ds = call.receive<Ns3DeviceStatus>()
            val created = service.createDeviceStatus(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                ds = ds,
            )
            call.response.header("Location", "/api/v3/devicestatus/${created.identifier}")
            call.respond(
                HttpStatusCode.Created,
                Ns3Response<Ns3DeviceStatus>(status = 201, identifier = created.identifier),
            )
        }
        get("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val ds = service.getDeviceStatus(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
            )
            if (ds == null) {
                call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3DeviceStatus>(status = 404))
            } else {
                call.respond(Ns3Response(status = 200, result = ds))
            }
        }
        delete("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val permanent = call.request.queryParameters["permanent"] == "true"
            service.deleteDeviceStatus(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                permanent = permanent,
            )
            call.respond(Ns3Response<Unit>(status = 200))
        }
    }
}

@Suppress("LongMethod")
private fun Route.nightscoutV3EntriesRoutes(
    service: NightscoutV3Service,
    maxLimit: Int,
    userSettingsClient: UserSettingsClient,
) {
    route("/api/v3/entries") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val params = call.parseNs3SearchParams(maxLimit)
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val entries = service.searchEntries(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                params = params,
                glucoseUnit = glucoseUnit,
            )
            call.respond(Ns3ListResponse(status = 200, result = entries))
        }
        post {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val entry = call.receive<Ns3Entry>()
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val created = service.createEntry(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                entry = entry,
                glucoseUnit = glucoseUnit,
            )
            call.response.header("Location", "/api/v3/entries/${created.identifier}")
            val createResponse = Ns3Response<Ns3Entry>(status = 201, identifier = created.identifier)
            call.respond(HttpStatusCode.Created, createResponse)
        }
        get("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val id = call.parameters["identifier"]!!
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val entry = service.getEntry(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                glucoseUnit = glucoseUnit,
            )
            if (entry == null) {
                call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Entry>(status = 404))
            } else {
                call.respond(Ns3Response(status = 200, result = entry))
            }
        }
        put("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val id = call.parameters["identifier"]!!
            val entry = call.receive<Ns3Entry>()
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val updated = service.updateEntry(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                entry = entry,
                glucoseUnit = glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        patch("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val id = call.parameters["identifier"]!!
            val entry = call.receive<Ns3Entry>()
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val updated = service.updateEntry(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                entry = entry,
                glucoseUnit = glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        delete("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val permanent = call.request.queryParameters["permanent"] == "true"
            service.deleteEntry(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                permanent = permanent,
            )
            call.respond(Ns3Response<Unit>(status = 200))
        }
        get("/history") {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val result = service.historyEntries(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                lastModified = null,
                glucoseUnit = glucoseUnit,
            )
            call.respond(result)
        }
        get("/history/{lastModified}") {
            val principal = call.principal<UserPrincipal>()!!
            val authorization = call.request.header("Authorization") ?: ""
            val lastModified = call.parameters["lastModified"]?.toLongOrNull()
            val glucoseUnit = userSettingsClient.getGlucoseUnit(authorization)
            val result = service.historyEntries(
                userId = principal.userId.toString(),
                authorization = authorization,
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                lastModified = lastModified,
                glucoseUnit = glucoseUnit,
            )
            call.respond(result)
        }
    }
}

private fun Route.nightscoutV3TreatmentsRoutes(service: NightscoutV3Service, maxLimit: Int) {
    route("/api/v3/treatments") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val params = call.parseNs3SearchParams(maxLimit)
            val treatments = service.searchTreatments(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                params = params,
            )
            call.respond(Ns3ListResponse(status = 200, result = treatments))
        }
        post {
            val principal = call.principal<UserPrincipal>()!!
            val treatment = call.receive<Ns3Treatment>()
            val created = service.createTreatment(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                treatment = treatment,
            )
            call.response.header("Location", "/api/v3/treatments/${created.identifier}")
            val createResponse = Ns3Response<Ns3Treatment>(status = 201, identifier = created.identifier)
            call.respond(HttpStatusCode.Created, createResponse)
        }
        get("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val treatment = service.getTreatment(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
            )
            if (treatment == null) {
                call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Treatment>(status = 404))
            } else {
                call.respond(Ns3Response(status = 200, result = treatment))
            }
        }
        put("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val treatment = call.receive<Ns3Treatment>()
            val updated = service.updateTreatment(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                treatment = treatment,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        patch("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val treatment = call.receive<Ns3Treatment>()
            val updated = service.updateTreatment(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                treatment = treatment,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        delete("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val permanent = call.request.queryParameters["permanent"] == "true"
            service.deleteTreatment(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                permanent = permanent,
            )
            call.respond(Ns3Response<Unit>(status = 200))
        }
    }
}

private fun Route.nightscoutV3FoodRoutes(service: NightscoutV3Service, maxLimit: Int) {
    route("/api/v3/food") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val params = call.parseNs3SearchParams(maxLimit)
            val foods = service.searchFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                params = params,
            )
            call.respond(Ns3ListResponse(status = 200, result = foods))
        }
        post {
            val principal = call.principal<UserPrincipal>()!!
            val food = call.receive<Ns3Food>()
            val created = service.createFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                food = food,
            )
            call.response.header("Location", "/api/v3/food/${created.identifier}")
            val createResponse = Ns3Response<Ns3Food>(status = 201, identifier = created.identifier)
            call.respond(HttpStatusCode.Created, createResponse)
        }
        get("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val food = service.getFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
            )
            if (food == null) {
                call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Food>(status = 404))
            } else {
                call.respond(Ns3Response(status = 200, result = food))
            }
        }
        put("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val food = call.receive<Ns3Food>()
            val updated = service.updateFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                food = food,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        patch("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val food = call.receive<Ns3Food>()
            val updated = service.updateFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                food = food,
            )
            call.respond(Ns3Response(status = 200, result = updated))
        }
        delete("/{identifier}") {
            val principal = call.principal<UserPrincipal>()!!
            val id = call.parameters["identifier"]!!
            val permanent = call.request.queryParameters["permanent"] == "true"
            service.deleteFood(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                id = id,
                permanent = permanent,
            )
            call.respond(Ns3Response<Unit>(status = 200))
        }
    }
}
