/* src/main/kotlin/services/PreguntasBackupSeeder.kt */

package services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID

@Serializable
data class PreguntaBackup(
    val pregunta_id: String,
    val tipo_banco: String,
    val sector: String,
    val nivel: String,
    val meta_cargo: String,
    val tipo_pregunta: String,
    val texto: String,
    val pistas: String,
    val config_respuesta: String,
    val config_evaluacion: String? = null,
    val fecha_creacion: String? = null
)

@Serializable
data class BackupContainer(
    val version: String = "1.0",
    val fecha_actualizacion: String? = null,
    val descripcion: String? = null,
    val total_preguntas: Int = 0,
    val preguntas: List<PreguntaBackup> = emptyList()
)

class PreguntasBackupSeeder(
    private val backupFilePath: String = "src/DB/preguntas_backup.json"
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Restaura preguntas desde JSON si (y solo si) la BD está vacía para el tipo_banco indicado.
     *
     * Además, si el archivo NO existe o existe pero está vacío/dañado, crea (o reescribe) un
     * JSON válido "vacío" para evitar fallos futuros.
     */
    fun seedIfEmpty(tipoBanco: String = "IAJOB") {
        val count = transaction {
            var cnt = 0
            val sql = """
                SELECT COUNT(*) AS cnt
                FROM pregunta
                WHERE tipo_banco = '${escSql(tipoBanco)}'
            """.trimIndent()

            TransactionManager.current().exec(sql) { rs ->
                if (rs.next()) cnt = rs.getInt("cnt")
            }
            cnt
        }

        if (count > 0) {
            println("🟦 Seed backup omitido: ya existen $count preguntas tipo_banco='$tipoBanco'.")
            return
        }

        val file = File(backupFilePath)
        ensureEmptyBackupFileExistsIfMissingOrEmpty(file, tipoBanco)

        if (!file.exists()) {
            // Por seguridad, si no se pudo crear, salimos sin romper startup
            println("🟨 Seed backup omitido: no existe el archivo $backupFilePath (y no se pudo crear).")
            return
        }

        val container: BackupContainer = try {
            val txt = file.readText().trim()
            if (txt.isBlank()) {
                // Archivo vacío → lo normalizamos a un container válido vacío y salimos
                writeEmptyBackup(file, tipoBanco)
                println("🟨 Seed backup omitido: archivo vacío; se normalizó a JSON vacío válido.")
                return
            }
            json.decodeFromString(BackupContainer.serializer(), txt)
        } catch (e: Exception) {
            // JSON inválido → lo normalizamos a uno válido vacío y salimos (no adivinamos datos)
            println("🟥 Seed backup falló: JSON inválido en $backupFilePath -> ${e.message}")
            writeEmptyBackup(file, tipoBanco)
            println("🟨 Se reescribió backup a JSON vacío válido para evitar futuros fallos.")
            return
        }

        if (container.preguntas.isEmpty()) {
            println("🟨 Seed backup omitido: el archivo existe pero no tiene preguntas.")
            return
        }

        val (intentadas, invalidasUuid) = transaction {
            var attempted = 0
            var badUuid = 0

            container.preguntas.forEach { p ->
                val pid = runCatching { UUID.fromString(p.pregunta_id) }.getOrNull()
                if (pid == null) {
                    println("⚠️ Seed: UUID inválido: ${p.pregunta_id} (omitida)")
                    badUuid++
                    return@forEach
                }

                attempted++

                val sql = """
                    INSERT INTO pregunta (
                        pregunta_id,
                        tipo_banco,
                        sector,
                        nivel,
                        meta_cargo,
                        tipo_pregunta,
                        texto,
                        pistas,
                        config_respuesta,
                        config_evaluacion,
                        activa,
                        fecha_creacion
                    )
                    VALUES (
                        '${escSql(p.pregunta_id)}',
                        '${escSql(p.tipo_banco)}',
                        '${escSql(p.sector)}',
                        '${escSql(p.nivel)}',
                        '${escSql(p.meta_cargo)}',
                        '${escSql(p.tipo_pregunta)}',
                        '${escSql(p.texto)}',
                        '${escSql(p.pistas)}'::jsonb,
                        '${escSql(p.config_respuesta)}'::jsonb,
                        ${if (p.config_evaluacion != null) "'${escSql(p.config_evaluacion)}'::jsonb" else "NULL"},
                        true,
                        now()
                    )
                    ON CONFLICT (pregunta_id) DO NOTHING
                """.trimIndent()

                TransactionManager.current().exec(sql)
            }

            attempted to badUuid
        }

        val finalCount = transaction {
            var cnt = 0
            val sql = """
                SELECT COUNT(*) AS cnt
                FROM pregunta
                WHERE tipo_banco = '${escSql(tipoBanco)}'
            """.trimIndent()
            TransactionManager.current().exec(sql) { rs -> if (rs.next()) cnt = rs.getInt("cnt") }
            cnt
        }

        println(
            "✅ Seed backup ejecutado desde $backupFilePath. " +
                "preguntas_en_json=${container.preguntas.size}, intentadas=$intentadas, invalidas_uuid=$invalidasUuid, total_en_bd=$finalCount"
        )
    }

    private fun ensureEmptyBackupFileExistsIfMissingOrEmpty(file: File, tipoBanco: String) {
        try {
            file.parentFile?.mkdirs()

            if (!file.exists()) {
                writeEmptyBackup(file, tipoBanco)
                println("🟩 Seed: creado backup vacío en ${file.path}")
                return
            }

            // Existe pero puede estar vacío
            if (file.length() == 0L) {
                writeEmptyBackup(file, tipoBanco)
                println("🟩 Seed: backup existente vacío; se reescribió a JSON vacío válido (${file.path})")
            }
        } catch (e: Exception) {
            println("🟥 Seed: no se pudo asegurar/crear backup vacío (${file.path}) -> ${e.message}")
        }
    }

    private fun writeEmptyBackup(file: File, tipoBanco: String) {
        val empty = BackupContainer(
            version = "1.0",
            fecha_actualizacion = java.time.LocalDateTime.now().toString(),
            descripcion = "Backup de preguntas generadas por IA para restauración. tipo_banco='$tipoBanco'.",
            total_preguntas = 0,
            preguntas = emptyList()
        )
        file.writeText(json.encodeToString(BackupContainer.serializer(), empty))
    }

    private fun escSql(s: String): String = s.replace("'", "''")
}
