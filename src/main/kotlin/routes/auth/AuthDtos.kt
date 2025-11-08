package routes.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ====== DTOs ======
@Serializable
data class RegisterReq(
    val email: String,
    val password: String,
    val nombre: String? = null,
    val idioma: String? = null,
    // Datos de perfil (opcionales)
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class LoginOk(val accessToken: String, val refreshToken: String? = null)
@Serializable data class ErrorRes(val error: String)

@Serializable data class RequestResetReq(val email: String)
@Serializable data class RequestResetOk(val ok: Boolean = true, val token: String, val code: String)
@Serializable data class ConfirmResetReq(val token: String, val code: String, val newPassword: String)
@Serializable data class OkRes(val ok: Boolean = true)

@Serializable data class RefreshReq(val refreshToken: String)
@Serializable data class RefreshOk(val accessToken: String, val refreshToken: String)
