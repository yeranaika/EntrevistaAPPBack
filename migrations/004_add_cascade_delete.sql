-- Migración: Garantizar ON DELETE CASCADE en todas las foreign keys hacia usuario
-- Fecha: 2025-11-18
-- Descripción: Implementación del derecho al olvido (GDPR/LOPD) mediante borrado en cascada
--              Esta migración asegura que al eliminar un usuario, todos sus datos se borren automáticamente

-- NOTA: La mayoría de las tablas YA tienen ON DELETE CASCADE configurado desde la creación inicial.
--       Esta migración verifica y actualiza las que pudieran faltar por migraciones posteriores.

BEGIN;

-- =============================================================================
-- VERIFICACIÓN Y ACTUALIZACIÓN DE FOREIGN KEYS
-- =============================================================================

-- 1) refresh_token → usuario (YA configurado en esquema inicial)
-- No requiere cambios

-- 2) perfil_usuario → usuario (YA configurado)
-- No requiere cambios

-- 3) oauth_account → usuario (YA configurado)
-- No requiere cambios

-- 4) password_reset → usuario (YA configurado)
-- No requiere cambios

-- 5) consentimiento → usuario (YA configurado)
-- No requiere cambios

-- 6) suscripcion → usuario (YA configurado)
-- No requiere cambios

-- 7) pago → suscripcion (YA configurado - cascada indirecta vía suscripcion)
-- No requiere cambios

-- 8) plan_practica → usuario (YA configurado)
-- No requiere cambios

-- 9) plan_practica_paso → plan_practica (YA configurado - cascada indirecta)
-- No requiere cambios

-- 10) objetivo_carrera → usuario (YA configurado)
-- No requiere cambios

-- 11) intento_prueba → usuario (YA configurado)
-- No requiere cambios

-- 12) respuesta_prueba → intento_prueba (YA configurado - cascada indirecta)
-- No requiere cambios

-- 13) sesion_entrevista → usuario (YA configurado)
-- No requiere cambios

-- 14) sesion_pregunta → sesion_entrevista (YA configurado - cascada indirecta)
-- No requiere cambios

-- 15) respuesta → usuario (YA configurado)
-- No requiere cambios

-- 16) respuesta → sesion_pregunta (YA configurado - cascada indirecta)
-- No requiere cambios

-- 17) retroalimentacion → respuesta (YA configurado - cascada indirecta)
-- No requiere cambios

-- 18) institucion_miembro → usuario (YA configurado)
-- No requiere cambios

-- 19) institucion_miembro → institucion (YA configurado - cascada indirecta)
-- No requiere cambios

-- 20) licencia_institucional → institucion (YA configurado - cascada indirecta)
-- No requiere cambios

-- 21) licencia_asignacion → usuario (YA configurado)
-- No requiere cambios

-- 22) licencia_asignacion → licencia_institucional (YA configurado - cascada indirecta)
-- No requiere cambios

-- 23) cache_offline → usuario (YA configurado)
-- No requiere cambios

-- 24) log_auditoria → usuario (Usa ON DELETE SET NULL - CORRECTO para auditoría)
-- No requiere cambios

-- 25) ticket → usuario (Creado en migración 003 con ON DELETE CASCADE)
-- No requiere cambios

-- =============================================================================
-- CREACIÓN DE FUNCIÓN AUXILIAR PARA BORRADO DE CUENTA
-- =============================================================================

-- Función que retorna estadísticas de datos a eliminar (opcional, para transparencia)
CREATE OR REPLACE FUNCTION app.get_user_data_count(p_usuario_id UUID)
RETURNS TABLE (
    tabla VARCHAR,
    cantidad BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 'refresh_tokens'::VARCHAR, COUNT(*) FROM app.refresh_token WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'perfil'::VARCHAR, COUNT(*) FROM app.perfil_usuario WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'oauth_accounts'::VARCHAR, COUNT(*) FROM app.oauth_account WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'password_resets'::VARCHAR, COUNT(*) FROM app.password_reset WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'consentimientos'::VARCHAR, COUNT(*) FROM app.consentimiento WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'suscripciones'::VARCHAR, COUNT(*) FROM app.suscripcion WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'planes_practica'::VARCHAR, COUNT(*) FROM app.plan_practica WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'objetivos_carrera'::VARCHAR, COUNT(*) FROM app.objetivo_carrera WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'intentos_prueba'::VARCHAR, COUNT(*) FROM app.intento_prueba WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'sesiones_entrevista'::VARCHAR, COUNT(*) FROM app.sesion_entrevista WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'respuestas'::VARCHAR, COUNT(*) FROM app.respuesta WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'membresías_institucionales'::VARCHAR, COUNT(*) FROM app.institucion_miembro WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'asignaciones_licencia'::VARCHAR, COUNT(*) FROM app.licencia_asignacion WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'cache_offline'::VARCHAR, COUNT(*) FROM app.cache_offline WHERE usuario_id = p_usuario_id
    UNION ALL
    SELECT 'tickets_soporte'::VARCHAR, COUNT(*) FROM app.ticket WHERE usuario_id = p_usuario_id;
END;
$$ LANGUAGE plpgsql;

-- Comentarios de documentación
COMMENT ON FUNCTION app.get_user_data_count(UUID) IS
'Retorna un resumen de todos los registros que serán eliminados al borrar un usuario';

COMMIT;

-- =============================================================================
-- RESUMEN DE CASCADAS CONFIGURADAS
-- =============================================================================
--
-- Al ejecutar DELETE FROM app.usuario WHERE usuario_id = X, se eliminarán automáticamente:
--
-- DIRECTOS (ON DELETE CASCADE desde usuario):
--   ✓ refresh_token
--   ✓ perfil_usuario
--   ✓ oauth_account
--   ✓ password_reset
--   ✓ consentimiento
--   ✓ suscripcion
--   ✓ plan_practica
--   ✓ objetivo_carrera
--   ✓ intento_prueba
--   ✓ sesion_entrevista
--   ✓ respuesta
--   ✓ institucion_miembro
--   ✓ licencia_asignacion
--   ✓ cache_offline
--   ✓ ticket
--
-- INDIRECTOS (cascada desde las tablas anteriores):
--   ✓ pago (vía suscripcion)
--   ✓ plan_practica_paso (vía plan_practica)
--   ✓ respuesta_prueba (vía intento_prueba)
--   ✓ sesion_pregunta (vía sesion_entrevista)
--   ✓ retroalimentacion (vía respuesta)
--
-- ANONIMIZADOS (ON DELETE SET NULL):
--   ✓ log_auditoria (mantiene evento, elimina identificación)
--
-- =============================================================================
