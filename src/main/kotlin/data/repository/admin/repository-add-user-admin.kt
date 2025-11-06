package data.repository.admin

import data.models.admin.AdminCreateUserReq
import data.models.admin.AdminCreateUserRes
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import at.favre.lib.crypto.bcrypt.BCrypt
import java.util.UUID

class AdminUserRepository(
    private val db: Database
) {
    private fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    suspend fun createByAdmin(req: AdminCreateUserReq): AdminCreateUserRes =
        newSuspendedTransaction(db = db) {
            val rol = req.rol.lowercase()
            require(rol == "user" || rol == "admin") { "rol inválido (user|admin)" }
            require(req.correo.isNotBlank()) { "correo requerido" }
            require(req.password.length >= 6) { "password mínimo 6 caracteres" }

            // DSL nueva: selectAll().where { ... }.limit(1)
            val yaExiste = UsuarioTable
                .selectAll()
                .where { UsuarioTable.correo eq req.correo }
                .limit(1)
                .any()
            if (yaExiste) error("correo ya registrado")

            val newId = UUID.randomUUID()

            UsuarioTable.insert { st ->
                st[UsuarioTable.usuarioId]      = newId
                st[UsuarioTable.correo]         = req.correo
                st[UsuarioTable.contrasenaHash] = hash(req.password)
                st[UsuarioTable.nombre]         = req.nombre
                st[UsuarioTable.idioma]         = req.idioma ?: "es"
                st[UsuarioTable.estado]         = "activo"
                st[UsuarioTable.rol]            = rol
            }

            AdminCreateUserRes(
                id     = newId.toString(),
                correo = req.correo,
                nombre = req.nombre,
                idioma = req.idioma ?: "es",
                rol    = rol
            )
        }
}
