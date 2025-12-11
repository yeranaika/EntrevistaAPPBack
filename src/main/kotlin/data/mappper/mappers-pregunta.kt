// data/mappper/mappers-pregunta.kt
package data.mapper

import data.models.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import data.tables.cuestionario.preguntas.PreguntaTable

fun ResultRow.toPreguntaRes(json: Json): PreguntaRes {
    val tipoBancoStr = this[PreguntaTable.tipoBanco]
    val nivelStr = this[PreguntaTable.nivel]

    return PreguntaRes(
        id = this[PreguntaTable.id].toString(),
        tipoBanco = runCatching { TipoBanco.valueOf(tipoBancoStr) }.getOrElse { TipoBanco.tec },
        nivel = runCatching { Nivel.valueOf(nivelStr) }.getOrElse { Nivel.jr },
        sector = this[PreguntaTable.sector],
        texto = this[PreguntaTable.texto],
        // pistas viene como JSONB -> String, lo decodificamos a Map<String,String> si se puede
        pistas = this[PreguntaTable.pistas]?.let {
            runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
        },
        // La tabla pregunta YA NO tiene columna historica.
        // Si tu DTO PreguntaRes aún tiene este campo, lo dejamos en null (o emptyMap()).
        historica = null,
        activa = this[PreguntaTable.activa],
        fechaCreacion = this[PreguntaTable.fechaCreacion].toString()
    )
}
