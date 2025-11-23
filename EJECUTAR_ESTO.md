# 🚀 EJECUTAR ESTO AHORA

## El Problema

No tienes preguntas en tu base de datos. Por eso el sistema dice "0 disponibles".

---

## ✅ Solución (1 minuto)

### Ejecutar este script SQL:

```bash
# En tu terminal de PostgreSQL
\i insert_preguntas_prueba.sql
```

O **copiar y pegar** el contenido de `insert_preguntas_prueba.sql` en tu cliente SQL.

---

## 📊 Qué Hace el Script

Inserta **12 preguntas de prueba** para "Desarrollador Full Stack":

- 4 preguntas nivel **Junior** (básicas)
- 4 preguntas nivel **Mid** (intermedias)  
- 4 preguntas nivel **Senior** (avanzadas)

**Temas:**
- HTML, CSS, JavaScript (básico)
- REST, ORM, JWT, Docker (intermedio)
- MVC, SOLID, Repository, CI/CD (avanzado)

---

## 🧪 Después de Ejecutar

### 1. Verificar en SQL

Deberías ver:

```
meta_cargo                  | nivel | total_preguntas
---------------------------+-------+----------------
Desarrollador Full Stack   | jr    | 4
Desarrollador Full Stack   | mid   | 4
Desarrollador Full Stack   | sr    | 4

total_preguntas_disponibles
---------------------------
12
```

### 2. Probar en Postman

1. Ir a Postman
2. Ejecutar: **"GET - Iniciar Test por Cargo"**
3. Debería devolver **200 OK** con 10 preguntas

**Respuesta esperada:**
```json
{
  "habilidad": "Desarrollador Full Stack",
  "preguntas": [
    {
      "id": "...",
      "enunciado": "¿Qué es HTML?",
      "opciones": ["...", "...", "...", "..."],
      "dificultad": 1
    },
    ...
  ],
  "totalPreguntas": 10
}
```

### 3. Verificar Variable

- Click en el ícono del ojo 👁️ en Postman
- Verificar que `test_cargo` = "Desarrollador Full Stack"

---

## 📋 Comandos Rápidos

### Opción 1: Desde psql
```bash
psql -U tu_usuario -d tu_base_de_datos -f insert_preguntas_prueba.sql
```

### Opción 2: Copiar y pegar
1. Abrir `insert_preguntas_prueba.sql`
2. Copiar todo (Ctrl+A, Ctrl+C)
3. Pegar en tu cliente SQL
4. Ejecutar

---

## ✅ Checklist

- [ ] Ejecutar `insert_preguntas_prueba.sql`
- [ ] Verificar que devuelva "12" preguntas
- [ ] Ir a Postman
- [ ] Ejecutar "Iniciar Test por Cargo"
- [ ] Verificar respuesta 200 OK
- [ ] Verificar que `test_cargo` se guarde
- [ ] Ejecutar "Evaluar Test" (reemplazar IDs)
- [ ] Ejecutar "Generar Plan"

---

## 🎉 Resultado Final

Después de esto, todo el flujo debería funcionar:

```
1. Register ✅
2. Onboarding ✅
3. Iniciar Test ✅ (ahora con preguntas)
4. Evaluar Test ✅
5. Generar Plan ✅
```

---

**EJECUTA `insert_preguntas_prueba.sql` AHORA** 🚀
