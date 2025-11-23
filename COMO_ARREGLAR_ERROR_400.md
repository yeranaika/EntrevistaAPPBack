# 🚨 Cómo Arreglar el Error 400

## El Problema

Cuando ejecutas "Iniciar Test por Cargo" en Postman, obtienes:

```json
{
  "error": "No hay suficientes preguntas disponibles para el cargo 'Desarrollador Full Stack'",
  "disponibles": 0,
  "solicitadas": 10
}
```

**Causa:** Las preguntas no tienen el campo `meta_cargo` poblado.

---

## ✅ La Solución (3 minutos)

### Paso 1: Abrir tu Cliente SQL

Opciones:
- **pgAdmin** (interfaz gráfica)
- **DBeaver** (interfaz gráfica)
- **psql** (línea de comandos)
- **DataGrip** (JetBrains)

### Paso 2: Conectar a tu Base de Datos

Usa las credenciales de tu archivo `.env`:

```
Host: localhost (o el que uses)
Port: 5432 (o el que uses)
Database: tu_base_de_datos
User: tu_usuario
Password: tu_password
```

### Paso 3: Ejecutar el Script SQL

**Opción A: Copiar y pegar**

Abre el archivo `fix_meta_cargo_rapido.sql` y copia todo el contenido en tu cliente SQL.

**Opción B: Ejecutar desde terminal**

```bash
# Windows (PowerShell)
$env:PGPASSWORD="tu_password"
psql -U tu_usuario -d tu_base_de_datos -f fix_meta_cargo_rapido.sql

# Linux/Mac
PGPASSWORD=tu_password psql -U tu_usuario -d tu_base_de_datos -f fix_meta_cargo_rapido.sql
```

### Paso 4: Verificar el Resultado

Deberías ver algo como:

```
DESPUÉS DE ACTUALIZAR | meta_cargo                  | nivel | total_preguntas
---------------------+-----------------------------+-------+----------------
DESPUÉS DE ACTUALIZAR | Desarrollador Full Stack   | jr    | 12
DESPUÉS DE ACTUALIZAR | Desarrollador Full Stack   | mid   | 12
DESPUÉS DE ACTUALIZAR | Desarrollador Full Stack   | sr    | 6
DESPUÉS DE ACTUALIZAR | Analista de Sistemas       | jr    | 12
...

resultado
-----------------------------------------
✅ TODAS LAS PREGUNTAS TIENEN CARGO
```

### Paso 5: Probar en Postman

1. Volver a Postman
2. Ejecutar "GET - Iniciar Test por Cargo"
3. Debería devolver **200 OK** con 10 preguntas

---

## 🎯 Script SQL Mínimo (Si tienes prisa)

Si solo quieres arreglar "Desarrollador Full Stack":

```sql
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- Verificar
SELECT COUNT(*) as preguntas_disponibles
FROM app.pregunta
WHERE tipo_banco = 'NV'
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true;
```

Debería devolver al menos 10 preguntas.

---

## 🔍 Troubleshooting

### "No tengo preguntas en la tabla"

Si la consulta devuelve 0 preguntas, necesitas ejecutar primero la migración que crea las preguntas:

```bash
# Buscar archivo de migración
ls migrations/*nivelacion*.sql

# Ejecutar
psql -U tu_usuario -d tu_base_de_datos -f migrations/007_insert_preguntas_nivelacion_completas.sql
```

### "No sé mis credenciales de PostgreSQL"

Revisa tu archivo `.env`:

```bash
cat .env | grep DB
```

Deberías ver algo como:
```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=entrevista_db
DB_USER=postgres
DB_PASSWORD=tu_password
```

### "No tengo psql instalado"

Descarga PostgreSQL desde: https://www.postgresql.org/download/

O usa un cliente gráfico como pgAdmin (viene con PostgreSQL).

---

## 📋 Checklist Rápido

- [ ] Abrir cliente SQL (pgAdmin, DBeaver, psql, etc.)
- [ ] Conectar a la base de datos
- [ ] Ejecutar `fix_meta_cargo_rapido.sql`
- [ ] Verificar que devuelva "✅ TODAS LAS PREGUNTAS TIENEN CARGO"
- [ ] Volver a Postman
- [ ] Ejecutar "Iniciar Test por Cargo"
- [ ] Verificar respuesta 200 OK con 10 preguntas
- [ ] Verificar que `test_cargo` se guarde en environment

---

## 🎉 Después de Arreglar

Una vez que ejecutes el script SQL, todo debería funcionar:

1. ✅ Iniciar Test por Cargo → 200 OK
2. ✅ Variable `test_cargo` guardada automáticamente
3. ✅ Evaluar Test usa el cargo automáticamente
4. ✅ Generar Plan funciona correctamente

---

## 💡 Resumen en 1 Línea

**Ejecuta `fix_meta_cargo_rapido.sql` en tu base de datos y vuelve a probar en Postman.**

---

¿Necesitas ayuda con algún paso específico? 🤔
