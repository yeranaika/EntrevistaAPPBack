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

    // Mapeo de valores antiguos a nuevos
    val tipoBancoMapped = when (tipoBancoStr.uppercase()) {
        "PR" -> TipoBanco.tec      // Práctica -> Técnica
        "BL" -> TipoBanco.soft     // Blandas -> Soft skills
        "NV" -> TipoBanco.mix      // Nivelación -> Mix
        else -> runCatching { TipoBanco.valueOf(tipoBancoStr) }.getOrElse { TipoBanco.tec }
    }

    val nivelMapped = when (nivelStr.lowercase()) {
        "junior" -> Nivel.jr
        "mid" -> Nivel.mid
        "senior" -> Nivel.sr
        else -> runCatching { Nivel.valueOf(nivelStr) }.getOrElse { Nivel.mid }
    }

    // Parsear pistas: puede ser array o map
    val pistasMap = this[PreguntaTable.pistas]?.let { pistasStr ->
        runCatching {
            // Intentar como Map<String, String>
            json.decodeFromString<Map<String, String>>(pistasStr)
        }.getOrElse {
            // Si falla, intentar como List<String> y convertir a Map
            runCatching {
                val list = json.decodeFromString<List<String>>(pistasStr)
                list.mapIndexed { index, value -> "pista${index + 1}" to value }.toMap()
            }.getOrNull()
        }
    }

    return PreguntaRes(
        id = this[PreguntaTable.id].toString(),
        tipoBanco = tipoBancoMapped,
        nivel = nivelMapped,
        sector = this[PreguntaTable.sector],
        metaCargo = this[PreguntaTable.metaCargo],
        texto = this[PreguntaTable.texto],
        pistas = pistasMap,
        // La tabla pregunta YA NO tiene columna historica.
        // Si tu DTO PreguntaRes aún tiene este campo, lo dejamos en null (o emptyMap()).
        historica = null,
        activa = this[PreguntaTable.activa],
        fechaCreacion = this[PreguntaTable.fechaCreacion].toString()
    )
}
