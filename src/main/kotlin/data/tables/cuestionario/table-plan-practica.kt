package data.tables.cuestionario

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import data.tables.usuarios.UsuarioTable   // 👈 IMPORTANTE

object PlanPracticaTable : UUIDTable("plan_practica", "plan_id") {

    // Columna UUID simple que referencia a usuario.usuario_id
    val usuarioId = uuid("usuario_id")
        .references(
            UsuarioTable.usuarioId,          // 👈 ajusta si tu columna se llama distinto
            onDelete = ReferenceOption.CASCADE
        )

    val area = varchar("area", 10).nullable()
    val metaCargo = varchar("meta_cargo", 120).nullable()
    val nivel = varchar("nivel", 20).nullable()
    val activo = bool("activo").default(true)
}

object PlanPracticaPasoTable : UUIDTable("plan_practica_paso", "paso_id") {

    // Aquí sí usamos reference a la PK de PlanPracticaTable (IdTable)
    val planId = reference(
        name = "plan_id",
        foreign = PlanPracticaTable,
        onDelete = ReferenceOption.CASCADE
    )

    val orden = integer("orden")
    val titulo = text("titulo")
    val descripcion = text("descripcion").nullable()
    val sesionesPorSemana = integer("sesiones_por_semana").nullable()
}
