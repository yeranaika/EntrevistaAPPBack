package data.repository.jobs

import data.tables.jobs.JobRequisitoTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import routes.jobs.JobRequirementItem
import java.util.UUID

class JobRequisitoRepository {

    fun replaceRequirements(
        cargo: String,
        area: String?,
        items: List<JobRequirementItem>
    ) {
        transaction {
            // Por ahora solo insertamos (no borramos anteriores).

            items.forEach { item ->
                // Técnicos
                item.requisitosTecnicos.forEach { req ->
                    JobRequisitoTable.insert {
                        it[id] = UUID.randomUUID()
                        it[JobRequisitoTable.area] = area
                        it[JobRequisitoTable.cargo] = cargo
                        it[nivelInferido] = item.nivelInferido
                        it[tipo] = "tecnico"
                        it[texto] = req
                        it[fuenteTitulo] = item.fuenteTitulo
                        it[empresa] = item.empresa
                        it[ubicacion] = item.ubicacion
                        it[urlAviso] = item.urlAviso
                    }
                }
                // Blandos
                item.requisitosBlandos.forEach { req ->
                    JobRequisitoTable.insert {
                        it[id] = UUID.randomUUID()
                        it[JobRequisitoTable.area] = area
                        it[JobRequisitoTable.cargo] = cargo
                        it[nivelInferido] = item.nivelInferido
                        it[tipo] = "blando"
                        it[texto] = req
                        it[fuenteTitulo] = item.fuenteTitulo
                        it[empresa] = item.empresa
                        it[ubicacion] = item.ubicacion
                        it[urlAviso] = item.urlAviso
                    }
                }
            }
        }
    }
}
