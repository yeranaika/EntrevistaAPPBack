# 📝 Ejemplos de Tests de Nivelación

Esta guía contiene ejemplos de cómo responder los tests de nivelación para cada área.

---

## 🎯 Desarrollo

### Ejemplo 1: Test Básico (Respuestas mayormente correctas)

**Request: GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10**

Después de obtener las preguntas, responde con:

```json
{
  "habilidad": "Desarrollo",
  "respuestas": [
    {
      "preguntaId": "ID_PREGUNTA_1",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_2",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_3",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_4",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_5",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_6",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_7",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_8",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_9",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_10",
      "respuestaSeleccionada": 0
    }
  ]
}
```

**Resultado esperado**: Nivel avanzado si respuestas ≥ 80%

---

## 📊 Analista TI

### Ejemplo: Test de Nivel Intermedio

```json
{
  "habilidad": "Analista TI",
  "respuestas": [
    {
      "preguntaId": "ID_PREGUNTA_1",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_2",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_3",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_4",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_5",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_6",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_7",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_8",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_9",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_10",
      "respuestaSeleccionada": 1
    }
  ]
}
```

**Resultado esperado**: Nivel intermedio si respuestas entre 60-79%

---

## 💼 Administración

### Ejemplo: Test de Nivel Básico

```json
{
  "habilidad": "Administracion",
  "respuestas": [
    {
      "preguntaId": "ID_PREGUNTA_1",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_2",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_3",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_4",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_5",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_6",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_7",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_8",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_9",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_10",
      "respuestaSeleccionada": 1
    }
  ]
}
```

**Resultado esperado**: Nivel básico si respuestas < 60%

---

## 🖥️ Ingeniería Informática

### Ejemplo: Test Completo

```json
{
  "habilidad": "Ingenieria Informatica",
  "respuestas": [
    {
      "preguntaId": "ID_PREGUNTA_1",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_2",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_3",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_4",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_5",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_6",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "ID_PREGUNTA_7",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_8",
      "respuestaSeleccionada": 2
    },
    {
      "preguntaId": "ID_PREGUNTA_9",
      "respuestaSeleccionada": 1
    },
    {
      "preguntaId": "ID_PREGUNTA_10",
      "respuestaSeleccionada": 0
    }
  ]
}
```

---

## 🔄 Cómo Usar Estos Ejemplos

### Método Manual (Postman):

1. **Iniciar Test**: `GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10`
2. **Copiar IDs**: De la respuesta, copia los `id` de cada pregunta
3. **Reemplazar**: En el JSON de ejemplo, reemplaza `ID_PREGUNTA_1`, `ID_PREGUNTA_2`, etc.
4. **Evaluar**: `POST /tests/nivelacion/evaluar` con el JSON modificado

### Método Automático (Script de Postman):

Puedes crear un Pre-request Script en Postman para generar respuestas aleatorias:

```javascript
// En el Pre-request Script del endpoint "Evaluar Test"
const preguntas = JSON.parse(pm.environment.get("test_preguntas") || "[]");
const habilidad = pm.environment.get("test_habilidad") || "Desarrollo";

const respuestas = preguntas.map(p => ({
    preguntaId: p.id,
    respuestaSeleccionada: Math.floor(Math.random() * 3) // 0, 1, o 2
}));

pm.environment.set("respuestas_generadas", JSON.stringify({
    habilidad: habilidad,
    respuestas: respuestas
}));
```

---

## 📊 Resultados Esperados por Nivel

### Nivel Avanzado (≥ 80%)

```json
{
  "testId": "uuid-123",
  "habilidad": "Desarrollo",
  "puntaje": 90,
  "totalPreguntas": 10,
  "preguntasCorrectas": 9,
  "nivelSugerido": "avanzado",
  "feedback": "¡Excelente trabajo! Has demostrado un dominio avanzado en Desarrollo...",
  "detalleRespuestas": [...]
}
```

**Plan Generado**: 6 pasos (arquitectura, optimización, proyecto complejo, etc.)

### Nivel Intermedio (60-79%)

```json
{
  "testId": "uuid-456",
  "habilidad": "Analista TI",
  "puntaje": 70,
  "totalPreguntas": 10,
  "preguntasCorrectas": 7,
  "nivelSugerido": "intermedio",
  "feedback": "¡Buen trabajo! Tienes un nivel intermedio en Analista TI...",
  "detalleRespuestas": [...]
}
```

**Plan Generado**: 5 pasos (fundamentos avanzados, frameworks, proyecto moderado, etc.)

### Nivel Básico (< 60%)

```json
{
  "testId": "uuid-789",
  "habilidad": "Administracion",
  "puntaje": 50,
  "totalPreguntas": 10,
  "preguntasCorrectas": 5,
  "nivelSugerido": "básico",
  "feedback": "Has completado el test de Administracion. Te recomendamos reforzar los conceptos básicos...",
  "detalleRespuestas": [...]
}
```

**Plan Generado**: 4 pasos (fundamentos, práctica guiada, proyecto simple, etc.)

---

## 🎯 Tips para Probar Diferentes Escenarios

### Escenario 1: Usuario Principiante
- Responder 4-5 preguntas correctamente (40-50%)
- Nivel esperado: **Básico**
- Plan: 4 pasos enfocados en fundamentos

### Escenario 2: Usuario Intermedio
- Responder 6-7 preguntas correctamente (60-70%)
- Nivel esperado: **Intermedio**
- Plan: 5 pasos con mayor complejidad

### Escenario 3: Usuario Avanzado
- Responder 8-10 preguntas correctamente (80-100%)
- Nivel esperado: **Avanzado**
- Plan: 6 pasos desafiantes

### Escenario 4: Múltiples Tests
- Realizar tests en diferentes habilidades
- Ver cómo el historial se va llenando
- Generar diferentes planes según resultados

---

## 🔍 Verificación de Resultados

### Ver Historial de Tests

```http
GET /tests/nivelacion/historial
```

Deberías ver todos los tests realizados:

```json
[
  {
    "id": "uuid-test-1",
    "habilidad": "Desarrollo",
    "puntaje": 90,
    "nivelSugerido": "avanzado",
    "fechaCompletado": "2025-01-22T10:30:00Z"
  },
  {
    "id": "uuid-test-2",
    "habilidad": "Analista TI",
    "puntaje": 70,
    "nivelSugerido": "intermedio",
    "fechaCompletado": "2025-01-22T11:00:00Z"
  }
]
```

### Ver Plan Generado

```http
GET /plan-practica
```

Deberías ver el plan personalizado:

```json
{
  "id": "uuid-plan",
  "area": "Desarrollo",
  "metaCargo": "Desarrollador Full Stack",
  "nivel": "sr",
  "pasos": [
    {
      "id": "uuid-paso-1",
      "orden": 1,
      "titulo": "Arquitectura de Software",
      "descripcion": "...",
      "sesionesPorSemana": 3
    },
    // ... 5 pasos más
  ]
}
```

---

**Última actualización**: Enero 2025
