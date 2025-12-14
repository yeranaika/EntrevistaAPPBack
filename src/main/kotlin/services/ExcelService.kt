package services

import data.models.admin.InformeGestionRes
import data.models.admin.ObjetivoCarreraExcel
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class ExcelService {

    fun generarExcelInforme(
        informe: InformeGestionRes,
        objetivosCarrera: List<ObjetivoCarreraExcel>
    ): ByteArray {
        val workbook = XSSFWorkbook()
        
        // ========== ESTILOS ==========
        // Título principal
        val titleFont = workbook.createFont().apply { 
            bold = true
            fontHeightInPoints = 16
            color = IndexedColors.WHITE.index
        }
        val titleStyle = workbook.createCellStyle().apply {
            setFont(titleFont)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }

        // Subtítulo
        val subtitleFont = workbook.createFont().apply { 
            bold = true
            fontHeightInPoints = 12
            color = IndexedColors.WHITE.index
        }
        val subtitleStyle = workbook.createCellStyle().apply {
            setFont(subtitleFont)
            fillForegroundColor = IndexedColors.GREY_50_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        // Headers de tabla
        val headerFont = workbook.createFont().apply { 
            bold = true
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.DARK_TEAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Celdas normales
        val normalCellStyle = workbook.createCellStyle().apply {
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Celdas alternadas (zebra)
        val altCellStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Estilo para valores positivos (verde)
        val greenStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Estilo para valores de alerta (amarillo)
        val yellowStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // =====================
        // HOJA 1: RESUMEN EJECUTIVO
        // =====================
        val resumenSheet = workbook.createSheet("Resumen Ejecutivo")
        var rowNum = 0
        
        // Título
        val titleRow = resumenSheet.createRow(rowNum++)
        titleRow.createCell(0).apply { 
            setCellValue("INFORME DE GESTION - ENTREVISTA APP")
            cellStyle = titleStyle
        }
        resumenSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 2))
        
        resumenSheet.createRow(rowNum++).createCell(0).setCellValue("Generado: ${java.time.LocalDateTime.now().toString().take(19)}")
        rowNum++

        // Métricas header
        val metricsHeader = resumenSheet.createRow(rowNum++)
        metricsHeader.createCell(0).apply { setCellValue("METRICA"); this.cellStyle = headerStyle }
        metricsHeader.createCell(1).apply { setCellValue("VALOR"); this.cellStyle = headerStyle }
        metricsHeader.createCell(2).apply { setCellValue("DESCRIPCION"); this.cellStyle = headerStyle }

        val usuariosConMeta = informe.usuarios.count { !it.metaCargo.isNullOrBlank() }
        val usuariosSinMeta = informe.usuarios.size - usuariosConMeta

        val metricas = listOf(
            Triple("Total Usuarios", informe.totales.usuariosRegistrados, "Usuarios registrados"),
            Triple("Usuarios Activos", informe.totales.usuariosActivos, "Cuentas activas"),
            Triple("Usuarios Inactivos", informe.totales.usuariosInactivos, "Cuentas inactivas"),
            Triple("Con Meta Definida", usuariosConMeta, "Tienen objetivo profesional"),
            Triple("Sin Meta Definida", usuariosSinMeta, "Oportunidad de engagement"),
            Triple("Suscripciones Premium", informe.totales.usuariosConPremium, "Plan de pago"),
            Triple("Plan Gratuito", informe.totales.usuariosConFree, "Plan free"),
            Triple("Suscripciones Activas", informe.totales.suscripcionesActivas, "Vigentes"),
            Triple("Suscripciones Inactivas", informe.totales.suscripcionesInactivas, "Vencidas/canceladas")
        )

        metricas.forEachIndexed { index, (nombre, valor, desc) ->
            val row = resumenSheet.createRow(rowNum++)
            val style = if (index % 2 == 0) normalCellStyle else altCellStyle
            row.createCell(0).apply { setCellValue(nombre); this.cellStyle = style }
            row.createCell(1).apply { setCellValue(valor.toDouble()); this.cellStyle = style }
            row.createCell(2).apply { setCellValue(desc); this.cellStyle = style }
        }

        (0..2).forEach { resumenSheet.autoSizeColumn(it) }

        // =====================
        // HOJA 2: METAS DE USUARIOS
        // =====================
        val metasSheet = workbook.createSheet("Metas de Usuarios")
        rowNum = 0

        val metasTitleRow = metasSheet.createRow(rowNum++)
        metasTitleRow.createCell(0).apply { 
            setCellValue("USUARIOS Y SUS METAS PROFESIONALES")
            this.cellStyle = titleStyle
        }
        metasSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 6))
        rowNum++

        val metasHeader = metasSheet.createRow(rowNum++)
        listOf("Correo", "Nombre", "Estado", "Tiene Meta?", "Cargo Meta", "Area", "Nivel").forEachIndexed { i, h ->
            metasHeader.createCell(i).apply { setCellValue(h); this.cellStyle = headerStyle }
        }

        informe.usuarios.forEachIndexed { index, u ->
            val row = metasSheet.createRow(rowNum++)
            val baseStyle = if (index % 2 == 0) normalCellStyle else altCellStyle
            val tieneMeta = !u.metaCargo.isNullOrBlank()
            
            row.createCell(0).apply { setCellValue(u.correo); this.cellStyle = baseStyle }
            row.createCell(1).apply { setCellValue(u.nombre ?: ""); this.cellStyle = baseStyle }
            row.createCell(2).apply { 
                setCellValue(u.estado)
                this.cellStyle = if (u.estado == "activo") greenStyle else yellowStyle 
            }
            row.createCell(3).apply { 
                setCellValue(if (tieneMeta) "SI" else "NO")
                this.cellStyle = if (tieneMeta) greenStyle else yellowStyle
            }
            row.createCell(4).apply { setCellValue(u.metaCargo ?: "-- Sin definir --"); this.cellStyle = baseStyle }
            row.createCell(5).apply { setCellValue(u.area ?: "-- Sin definir --"); this.cellStyle = baseStyle }
            row.createCell(6).apply { setCellValue(u.nivel ?: "-- Sin definir --"); this.cellStyle = baseStyle }
        }

        (0..6).forEach { metasSheet.autoSizeColumn(it) }

        // Resumen
        rowNum += 2
        val resumenMetasRow = metasSheet.createRow(rowNum++)
        resumenMetasRow.createCell(0).apply { setCellValue("RESUMEN DE METAS"); this.cellStyle = subtitleStyle }
        metasSheet.addMergedRegion(CellRangeAddress(rowNum-1, rowNum-1, 0, 1))
        rowNum++

        val rm1 = metasSheet.createRow(rowNum++)
        rm1.createCell(0).apply { setCellValue("Con meta:"); this.cellStyle = normalCellStyle }
        rm1.createCell(1).apply { setCellValue(usuariosConMeta.toDouble()); this.cellStyle = greenStyle }

        val rm2 = metasSheet.createRow(rowNum++)
        rm2.createCell(0).apply { setCellValue("Sin meta:"); this.cellStyle = normalCellStyle }
        rm2.createCell(1).apply { setCellValue(usuariosSinMeta.toDouble()); this.cellStyle = yellowStyle }

        // =====================
        // HOJA 3: SUSCRIPCIONES
        // =====================
        val suscSheet = workbook.createSheet("Suscripciones")
        rowNum = 0

        val suscTitleRow = suscSheet.createRow(rowNum++)
        suscTitleRow.createCell(0).apply { 
            setCellValue("DETALLE DE SUSCRIPCIONES")
            this.cellStyle = titleStyle
        }
        suscSheet.addMergedRegion(CellRangeAddress(0, 0, 0, 5))
        rowNum++

        val suscHeader = suscSheet.createRow(rowNum++)
        listOf("Correo", "Nombre", "Plan", "Estado Cuenta", "Estado Suscripcion", "Expiracion").forEachIndexed { i, h ->
            suscHeader.createCell(i).apply { setCellValue(h); this.cellStyle = headerStyle }
        }

        informe.usuarios.forEachIndexed { index, u ->
            val row = suscSheet.createRow(rowNum++)
            val baseStyle = if (index % 2 == 0) normalCellStyle else altCellStyle
            val esPremium = u.planSuscripcion != null && u.planSuscripcion != "free"
            
            row.createCell(0).apply { setCellValue(u.correo); this.cellStyle = baseStyle }
            row.createCell(1).apply { setCellValue(u.nombre ?: ""); this.cellStyle = baseStyle }
            row.createCell(2).apply { 
                setCellValue(u.planSuscripcion ?: "free")
                this.cellStyle = if (esPremium) greenStyle else baseStyle 
            }
            row.createCell(3).apply { setCellValue(u.estado); this.cellStyle = baseStyle }
            row.createCell(4).apply { setCellValue(u.estadoSuscripcion ?: "sin suscripcion"); this.cellStyle = baseStyle }
            row.createCell(5).apply { setCellValue(u.fechaExpiracionSuscripcion?.take(10) ?: "N/A"); this.cellStyle = baseStyle }
        }

        (0..5).forEach { suscSheet.autoSizeColumn(it) }

        // Resumen
        rowNum += 2
        val premiumCount = informe.usuarios.count { it.planSuscripcion != null && it.planSuscripcion != "free" }
        val freeCount = informe.usuarios.size - premiumCount

        suscSheet.createRow(rowNum++).createCell(0).apply { setCellValue("RESUMEN"); this.cellStyle = subtitleStyle }

        val s1 = suscSheet.createRow(rowNum++)
        s1.createCell(0).apply { setCellValue("Premium:"); this.cellStyle = normalCellStyle }
        s1.createCell(1).apply { setCellValue(premiumCount.toDouble()); this.cellStyle = greenStyle }

        val s2 = suscSheet.createRow(rowNum++)
        s2.createCell(0).apply { setCellValue("Free:"); this.cellStyle = normalCellStyle }
        s2.createCell(1).apply { setCellValue(freeCount.toDouble()); this.cellStyle = normalCellStyle }

        val conversionRate = if (informe.usuarios.isNotEmpty()) (premiumCount * 100 / informe.usuarios.size) else 0
        val s3 = suscSheet.createRow(rowNum++)
        s3.createCell(0).apply { setCellValue("Conversion:"); this.cellStyle = normalCellStyle }
        s3.createCell(1).apply { setCellValue("$conversionRate%"); this.cellStyle = if (conversionRate > 20) greenStyle else yellowStyle }

        // Generar bytes
        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()
        
        return out.toByteArray()
    }

    // ========== GENERAR CSV ==========
    fun generarCsvInforme(informe: InformeGestionRes): String {
        val sb = StringBuilder()
        
        // Header
        sb.appendLine("Correo,Nombre,Estado,Plan,Estado Suscripcion,Cargo Meta,Area,Nivel,Fecha Registro,Ultimo Login")
        
        // Datos
        informe.usuarios.forEach { u ->
            sb.appendLine("${u.correo},${u.nombre ?: ""},${u.estado},${u.planSuscripcion ?: "free"},${u.estadoSuscripcion ?: ""},${u.metaCargo ?: ""},${u.area ?: ""},${u.nivel ?: ""},${u.fechaCreacion?.take(10) ?: ""},${u.fechaUltimoLogin?.take(10) ?: ""}")
        }
        
        return sb.toString()
    }
}
