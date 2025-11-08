package data.mapper

import data.models.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

import tables.cuestionario.preguntas.PreguntaTable

fun ResultRow.toPreguntaRes(json: Json) = PreguntaRes(
    id = this[PreguntaTable.id].value.toString(),
    tipoBanco = TipoBanco.valueOf(this[PreguntaTable.tipoBanco]),
    nivel = Nivel.valueOf(this[PreguntaTable.nivel]),
    sector = this[PreguntaTable.sector],
    texto = this[PreguntaTable.texto],
    pistas = this[PreguntaTable.pistas]?.let { json.decodeFromString<Map<String, String>>(it) },
    historica = this[PreguntaTable.historica]?.let { json.decodeFromString<Map<String, String>>(it) },
    fechaCreacion = this[PreguntaTable.fechaCreacion].toString(),
    activa = this[PreguntaTable.activa]
)
