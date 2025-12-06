package services

/**
 * Modelo interno para generar feedback basado en preguntas corregidas.
 * No se expone por API, solo se usa dentro del servidor.
 */
data class ResultadoPreguntaResConTexto(
    val preguntaId: String,
    val textoPregunta: String,      // texto de la pregunta (o placeholder si aún no lo tienes en la tabla)
    val correcta: Boolean,
    val tipo: String,               // "abierta" | "opcion_multiple"
    val respuestaUsuario: String?   // texto de la respuesta o ids seleccionados
)
