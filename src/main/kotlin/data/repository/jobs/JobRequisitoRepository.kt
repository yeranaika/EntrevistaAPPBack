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
            items.forEach { item ->
                // Técnicos
                item.requisitosTecnicos.forEach { req: String ->
                    JobRequisitoTable.insert { row ->
                        row[JobRequisitoTable.id] = UUID.randomUUID()
                        row[JobRequisitoTable.area] = area
                        row[JobRequisitoTable.cargo] = cargo
                        row[JobRequisitoTable.nivelInferido] = item.nivelInferido
                        row[JobRequisitoTable.tipo] = "tecnico"
                        row[JobRequisitoTable.texto] = req
                        row[JobRequisitoTable.fuenteTitulo] = item.fuenteTitulo
                        row[JobRequisitoTable.empresa] = item.empresa
                        row[JobRequisitoTable.ubicacion] = item.ubicacion
                        row[JobRequisitoTable.urlAviso] = item.urlAviso
                    }
                }
                // Blandos
                item.requisitosBlandos.forEach { req: String ->
                    JobRequisitoTable.insert { row ->
                        row[JobRequisitoTable.id] = UUID.randomUUID()
                        row[JobRequisitoTable.area] = area
                        row[JobRequisitoTable.cargo] = cargo
                        row[JobRequisitoTable.nivelInferido] = item.nivelInferido
                        row[JobRequisitoTable.tipo] = "blando"
                        row[JobRequisitoTable.texto] = req
                        row[JobRequisitoTable.fuenteTitulo] = item.fuenteTitulo
                        row[JobRequisitoTable.empresa] = item.empresa
                        row[JobRequisitoTable.ubicacion] = item.ubicacion
                        row[JobRequisitoTable.urlAviso] = item.urlAviso
                    }
                }
            }
        }
    }
}
