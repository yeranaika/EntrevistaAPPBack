package routes.me

import data.repository.ProfileRepository
import data.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

// ---------- DTOs ----------
@Serializable
data class MeRes(
    val id: String,
    val email: String,
    val nombre: String? = null,
    val idioma: String? = null,          // ← nullable, calza con UserRow.idioma
    val perfil: PerfilRes? = null
)

@Serializable
data class PerfilRes(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable
data class PutMeReq(
    val nombre: String? = null,
    val idioma: String? = null
)

@Serializable
data class PutPerfilReq(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable data class OkRes(val ok: Boolean = true)
@Serializable data class ErrorRes(val error: String)

// ---------- Helper ----------
private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

// ---------- Routes ----------
fun Route.meRoutes(
    users: UserRepository,
    profiles: ProfileRepository
) {
    authenticate("auth-jwt") {
        route("/me") {

            // GET /me → usuario + perfil (si existe)
            get {
                val uid = call.userIdFromJwt()
                val u = users.findById(uid)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorRes("user_not_found"))

                // ✅ usa el método real del repo
                val p = profiles.findByUser(uid)

                val perfilRes = p?.let {
                    PerfilRes(
                        nivelExperiencia   = it.nivelExperiencia,
                        area               = it.area,
                        pais               = it.pais,
                        notaObjetivos      = it.notaObjetivos,
                        flagsAccesibilidad = it.flagsAccesibilidad
                    )
                }

                call.respond(
                    MeRes(
                        id = u.id.toString(),
                        email = u.email,
                        nombre = u.nombre,
                        idioma = u.idioma,
                        perfil = perfilRes
                    )
                )
            }

            // PUT /me → actualizar nombre/idioma (parcial)
            put {
                val uid = call.userIdFromJwt()
                val req = runCatching { call.receive<PutMeReq>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
                }

                var touched = 0
                if (req.nombre != null) touched += users.updateNombre(uid, req.nombre)
                if (req.idioma  != null) touched += users.updateIdioma(uid, req.idioma)

                if (touched == 0) return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("nothing_to_update"))
                call.respond(OkRes())
            }

            // GET /me/perfil → solo perfil
            get("/perfil") {
                val uid = call.userIdFromJwt()
                val p = profiles.findByUser(uid)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorRes("profile_not_found"))

                call.respond(
                    PerfilRes(
                        nivelExperiencia   = p.nivelExperiencia,
                        area               = p.area,
                        pais               = p.pais,
                        notaObjetivos      = p.notaObjetivos,
                        flagsAccesibilidad = p.flagsAccesibilidad
                    )
                )
            }

            // PUT /me/perfil → upsert de perfil_usuario
            put("/perfil") {
                val uid = call.userIdFromJwt()
                val req = runCatching { call.receive<PutPerfilReq>() }.getOrElse {
                    return@put call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
                }

                val existing = profiles.findByUser(uid)
                if (existing != null) {
                    // ✅ actualiza por perfilId (firma real del repo)
                    profiles.updatePartial(
                        perfilId = existing.perfilId,
                        nivelExperiencia   = req.nivelExperiencia,
                        area               = req.area,
                        pais               = req.pais,
                        notaObjetivos      = req.notaObjetivos,
                        flagsAccesibilidad = req.flagsAccesibilidad
                    )
                } else {
                    // ✅ crea por userId (firma real del repo)
                    profiles.create(
                        userId             = uid,
                        nivelExperiencia   = req.nivelExperiencia,
                        area               = req.area,
                        pais               = req.pais,
                        notaObjetivos      = req.notaObjetivos,
                        flagsAccesibilidad = req.flagsAccesibilidad
                    )
                }
                call.respond(OkRes())
            }
        }
    }
}
