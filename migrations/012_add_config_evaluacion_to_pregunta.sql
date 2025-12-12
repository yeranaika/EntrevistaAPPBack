-- migrations/012_add_config_evaluacion_to_pregunta.sql

ALTER TABLE pregunta 
ADD COLUMN IF NOT EXISTS config_evaluacion JSONB;

COMMENT ON COLUMN pregunta.config_evaluacion IS 
'Configuración para evaluación NLP/STAR de respuestas. 
Formato:
- Para choice: {"tipo_item":"choice","nlp":{"explicacion_correcta":"...","explicacion_incorrecta":"..."}}
- Para open: {"tipo_item":"open","nlp":{"frases_clave_esperadas":[...]},"feedback_generico":"..."}';