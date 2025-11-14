// src/main/kotlin/data/tables/billing/table-suscripcion.kt
package data.tables.billing

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import data.tables.usuarios.UsuarioTable   // deja este import

object SuscripcionTable : UUIDTable("suscripcion", "suscripcion_id") {

    // 👇 AQUÍ EL CAMBIO:
    // Si en tu UsuarioTable la PK se llama "usuarioId", usa eso:
    val usuarioId = reference("usuario_id", UsuarioTable.usuarioId)

    // Si tu PK se llama distinto (por ejemplo "id"), sería:
    // val usuarioId = reference("usuario_id", UsuarioTable.id)

    val plan = varchar("plan", 100)          // ej: "premium_mensual"
    val proveedor = varchar("proveedor", 50) // ej: "google_play"
    val estado = varchar("estado", 20)       // "active" | "canceled" | "expired"
    val fechaInicio = timestamp("fecha_inicio")
    val fechaRenovacion = timestamp("fecha_renovacion").nullable()
    val fechaExpiracion = timestamp("fecha_expiracion").nullable()
}
