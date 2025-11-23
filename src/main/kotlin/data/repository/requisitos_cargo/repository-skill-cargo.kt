package data.repository.requisitos_cargo

import data.models.requisitos_cargo.SkillsResponse
import data.tables.requisitos_cargo.SkillsCargoTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SkillsCargoRepository {

    suspend fun getSkillsByCargo(cargo: String): SkillsResponse {
        val tecnicas = mutableListOf<String>()
        val blandas  = mutableListOf<String>()

        transaction {
            SkillsCargoTable
                .selectAll()
                .where { SkillsCargoTable.cargo eq cargo }  // no se importa, se resuelve automáticamente
                .forEach { row ->
                    val descripcion = row[SkillsCargoTable.descripcion]
                    when (row[SkillsCargoTable.tipo]) {
                        "tecnico" -> tecnicas += descripcion
                        else      -> blandas  += descripcion
                    }
                }
        }

        return SkillsResponse(tecnicas, blandas)
    }
}
